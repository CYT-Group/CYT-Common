package group.cyt.common.module.adapter.google;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.util.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Closed binary attachment contract. Never fetch caller URLs or filesystem paths. */
public final class GmailAttachments {
  public static final int MAX_FILE = 10 * 1024 * 1024;
  private static final Map<String, List<String>> TYPES =
      Map.of(
          "image/png",
          List.of(".png"),
          "image/jpeg",
          List.of(".jpg", ".jpeg"),
          "image/gif",
          List.of(".gif"),
          "image/webp",
          List.of(".webp"),
          "video/mp4",
          List.of(".mp4"),
          "video/webm",
          List.of(".webm"),
          "application/pdf",
          List.of(".pdf"));

  private GmailAttachments() {}

  public static byte[] validate(String filename, String type, String encoded) {
    if (filename.isBlank()
        || filename.length() > 200
        || filename.matches("(?s).*[\\/\\\\\\r\\n\\x00].*")
        || !TYPES.containsKey(type)
        || TYPES.get(type).stream().noneMatch(filename.toLowerCase(Locale.ROOT)::endsWith)
        || encoded.length() > 4 * ((MAX_FILE + 2) / 3))
      throw GatewayException.invalid("附件名称、类型或大小不受支持");
    byte[] bytes;
    try {
      if (!encoded.matches("[A-Za-z0-9+/]*={0,2}") || encoded.length() % 4 != 0)
        throw new IllegalArgumentException();
      bytes = Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException e) {
      throw GatewayException.invalid("附件必须是有效 Base64 内容");
    }
    if (bytes.length == 0 || bytes.length > MAX_FILE)
      throw GatewayException.invalid("单个附件须为 1 字节至 10 MiB");
    boolean valid =
        switch (type) {
          case "image/png" ->
              starts(bytes, new byte[] {(byte) 0x89, 80, 78, 71, 13, 10, 26, 10}, 0);
          case "image/jpeg" -> starts(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}, 0);
          case "image/gif" ->
              starts(bytes, "GIF87a".getBytes(StandardCharsets.US_ASCII), 0)
                  || starts(bytes, "GIF89a".getBytes(StandardCharsets.US_ASCII), 0);
          case "image/webp" ->
              starts(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII), 0)
                  && starts(bytes, "WEBP".getBytes(StandardCharsets.US_ASCII), 8);
          case "video/mp4" ->
              bytes.length >= 12 && starts(bytes, "ftyp".getBytes(StandardCharsets.US_ASCII), 4);
          case "video/webm" -> starts(bytes, new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3}, 0);
          default -> starts(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII), 0);
        };
    if (!valid) throw GatewayException.invalid("附件内容与类型不一致");
    return bytes;
  }

  private static boolean starts(byte[] data, byte[] magic, int offset) {
    return data.length >= offset + magic.length
        && Arrays.equals(Arrays.copyOfRange(data, offset, offset + magic.length), magic);
  }

  public static Map<String, Object> find(Map<String, Object> part, String id, int depth) {
    if (depth > 20) throw GatewayException.invalid("邮件 MIME 嵌套层数过多");
    if (id.equals(JsonUtils.str(JsonUtils.map(part.get("body")), "attachmentId"))) return part;
    for (Object child : JsonUtils.list(part.get("parts"))) {
      var result = find(JsonUtils.map(child), id, depth + 1);
      if (result != null) return result;
    }
    return null;
  }

  private static String header(String value) {
    if (value.contains("\r") || value.contains("\n") || value.indexOf(0) >= 0)
      throw GatewayException.invalid("邮件头不能包含换行或空字节");
    return value;
  }

  private static String encoded(String value) {
    return "=?UTF-8?B?"
        + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
        + "?=";
  }

  private static String body(String type, String value) {
    return "Content-Type: "
        + type
        + "; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"
        + Base64.getMimeEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
        + "\r\n";
  }

  public static String mime(Map<String, Object> p) {
    StringBuilder out = new StringBuilder("MIME-Version: 1.0\r\n");
    String account = JsonUtils.str(p, "account");
    if (!account.isBlank()) out.append("From: ").append(header(account)).append("\r\n");
    for (String field : List.of("to", "cc", "bcc")) {
      var rows = JsonUtils.list(p.get(field));
      if (!rows.isEmpty())
        out.append(field)
            .append(": ")
            .append(header(String.join(", ", rows.stream().map(Object::toString).toList())))
            .append("\r\n");
    }
    out.append("Subject: ").append(encoded(header(JsonUtils.str(p, "subject")))).append("\r\n");
    String reply = JsonUtils.str(p, "in_reply_to");
    if (!reply.isBlank()) {
      messageId(reply);
      out.append("In-Reply-To: ").append(reply).append("\r\n");
    }
    var references = JsonUtils.list(p.get("references"));
    for (Object ref : references) messageId(ref.toString());
    if (!references.isEmpty())
      out.append("References: ")
          .append(String.join(" ", references.stream().map(Object::toString).toList()))
          .append("\r\n");
    var files = JsonUtils.list(p.get("attachments"));
    if (!files.isEmpty() && account.isBlank()) throw GatewayException.invalid("附件发送必须指定实际授权邮箱");
    List<byte[]> content = new ArrayList<>();
    long total = 0;
    for (Object file : files) {
      var f = JsonUtils.map(file);
      byte[] bytes =
          validate(
              JsonUtils.str(f, "filename"),
              JsonUtils.str(f, "mime_type"),
              JsonUtils.str(f, "content_base64"));
      content.add(bytes);
      total += bytes.length;
    }
    if (files.size() > 5 || total > 20L * 1024 * 1024)
      throw GatewayException.invalid("附件最多 5 个，合计不能超过 20 MiB");
    String text = body("text/plain", JsonUtils.str(p, "body_text"));
    if (!JsonUtils.str(p, "body_html").isBlank()) {
      String boundary = "cyt-alt-" + UUID.randomUUID();
      text =
          "Content-Type: multipart/alternative; boundary=\""
              + boundary
              + "\"\r\n\r\n--"
              + boundary
              + "\r\n"
              + text
              + "--"
              + boundary
              + "\r\n"
              + body("text/html", JsonUtils.str(p, "body_html"))
              + "--"
              + boundary
              + "--\r\n";
    }
    if (files.isEmpty()) return out.append(text).toString();
    String boundary = "cyt-mixed-" + UUID.randomUUID();
    out.append("Content-Type: multipart/mixed; boundary=\"")
        .append(boundary)
        .append("\"\r\n\r\n--")
        .append(boundary)
        .append("\r\n")
        .append(text);
    for (int i = 0; i < files.size(); i++) {
      var f = JsonUtils.map(files.get(i));
      out.append("--")
          .append(boundary)
          .append("\r\nContent-Type: ")
          .append(f.get("mime_type"))
          .append("\r\nContent-Disposition: attachment; filename=\"")
          .append(encoded(JsonUtils.str(f, "filename")))
          .append("\"\r\nContent-Transfer-Encoding: base64\r\n\r\n")
          .append(Base64.getMimeEncoder().encodeToString(content.get(i)))
          .append("\r\n");
    }
    return out.append("--").append(boundary).append("--\r\n").toString();
  }

  private static void messageId(String value) {
    if (value.length() > 998 || !value.matches("<[^<>\\s]+@[^<>\\s]+>"))
      throw GatewayException.invalid("回复引用必须为有效 Message-ID");
  }
}
