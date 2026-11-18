package group.cyt.common.core.http;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** A one-shot request source, explicitly carried into the executor; never a JSON parameter. */
public final class BinaryUpload {
  public static final int CHUNK_BYTES = 64 * 1024;

  @FunctionalInterface
  public interface Source {
    InputStream open() throws IOException;
  }

  private final Source source;
  private final String declaredLength, transferEncoding, contentType;
  private final long maxBytes;
  private final double timeoutSeconds;
  private final AtomicBoolean used = new AtomicBoolean();
  private volatile InputStream input;
  private volatile Runnable cancelUpstream = () -> {};
  private volatile boolean cancelled, completed;
  private volatile GatewayException failure;
  private long length, deadline;

  public BinaryUpload(
      Source source,
      String length,
      String transferEncoding,
      String contentType,
      GatewaySettings settings) {
    this.source = source;
    this.declaredLength = length;
    this.transferEncoding = transferEncoding;
    this.contentType =
        contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    try {
      maxBytes = Long.parseLong(settings.get("NAS_UPLOAD_MAX_BYTES", "536870912"));
      timeoutSeconds = Double.parseDouble(settings.get("NAS_UPLOAD_TIMEOUT_S", "300"));
      if (maxBytes < 0
          || !Double.isFinite(timeoutSeconds)
          || timeoutSeconds <= 0
          || timeoutSeconds > 86400) throw new IllegalArgumentException();
    } catch (IllegalArgumentException e) {
      throw GatewayException.config("NAS 上传大小或超时配置无效");
    }
  }

  /** Called only after authentication, exact authorization, quota and query validation. */
  public void prepare() {
    if (transferEncoding != null && !transferEncoding.isBlank()
        || declaredLength == null
        || !declaredLength.matches("[0-9]+"))
      throw new GatewayException(
          411, "length_required", "上传必须提供准确 Content-Length，不支持 Transfer-Encoding", false);
    try {
      length = Long.parseLong(declaredLength);
    } catch (NumberFormatException e) {
      throw new GatewayException(413, "payload_too_large", "文件超过上传大小限制", false);
    }
    if (length > maxBytes)
      throw new GatewayException(413, "payload_too_large", "文件超过上传大小限制", false);
    if (contentType.length() > 200
        || contentType.chars().anyMatch(c -> c < 32 || c == 127)
        || okhttp3.MediaType.parse(contentType) == null)
      throw GatewayException.invalid("Content-Type 必须为文件 MIME 类型");
    var mime = okhttp3.MediaType.get(contentType);
    if (mime.type().equals("multipart")
        || contentType
            .toLowerCase(java.util.Locale.ROOT)
            .startsWith("application/x-www-form-urlencoded"))
      throw GatewayException.invalid("上传仅接收原始文件流，不支持 multipart 或表单编码");
    deadline = System.nanoTime() + (long) (timeoutSeconds * 1_000_000_000L);
  }

  public long length() {
    return length;
  }

  public String contentType() {
    return contentType;
  }

  public double timeoutSeconds() {
    return timeoutSeconds;
  }

  public boolean completed() {
    return completed;
  }

  public GatewayException failure() {
    return failure;
  }

  public void onCancel(Runnable cancel) {
    cancelUpstream = cancel;
    if (cancelled) cancel.run();
  }

  public void cancel() {
    cancelled = true;
    cancelUpstream.run();
    // Closing a servlet input may block; never delay the caller's total timeout response.
    var stream = input;
    if (stream != null)
      Thread.startVirtualThread(
          () -> {
            try {
              stream.close();
            } catch (IOException ignored) {
            }
          });
  }

  private void checkDeadline() {
    if (cancelled || Thread.currentThread().isInterrupted() || System.nanoTime() > deadline)
      throw new GatewayException(504, "upstream_timeout", "文件上传超时", false);
  }

  /** Keep the final <=64KiB until EOF confirms the exact length. No whole-file buffering. */
  public void writeTo(OutputStream output) throws IOException {
    if (!used.compareAndSet(false, true)) throw GatewayException.invalid("文件流不能重放");
    try {
      checkDeadline();
      try {
        input = source.open();
      } catch (IOException e) {
        throw clientFailure();
      }
      byte[] block = new byte[CHUNK_BYTES];
      long remaining = length;
      while (remaining > CHUNK_BYTES) {
        readBlock(block, CHUNK_BYTES);
        checkDeadline();
        output.write(block, 0, CHUNK_BYTES);
        remaining -= CHUNK_BYTES;
      }
      int last = (int) remaining;
      readBlock(block, last);
      checkDeadline();
      if (read(block, 0, 1) != -1) throw GatewayException.invalid("文件实际长度与 Content-Length 不一致");
      checkDeadline();
      if (last > 0) output.write(block, 0, last);
      completed = true;
    } catch (GatewayException e) {
      failure = e;
      throw e;
    }
  }

  private int read(byte[] block, int offset, int size) {
    try {
      return input.read(block, offset, size);
    } catch (IOException e) {
      throw clientFailure();
    }
  }

  private GatewayException clientFailure() {
    checkAndRememberTimeout();
    failure = GatewayException.invalid("文件接收中断或实际长度与 Content-Length 不一致");
    return failure;
  }

  private void checkAndRememberTimeout() {
    try {
      checkDeadline();
    } catch (GatewayException e) {
      failure = e;
      throw e;
    }
  }

  private void readBlock(byte[] block, int size) throws IOException {
    int offset = 0;
    while (offset < size) {
      checkDeadline();
      int n = read(block, offset, size - offset);
      if (n < 0) throw GatewayException.invalid("文件实际长度小于 Content-Length");
      if (n == 0) continue;
      offset += n;
    }
  }

  /** Validate an empty object before an upstream Content-Length: 0 can commit it. */
  public void verifyEmpty() {
    if (length != 0) return;
    try {
      writeTo(OutputStream.nullOutputStream());
    } catch (IOException e) {
      throw GatewayException.invalid("文件接收中断");
    }
  }
}
