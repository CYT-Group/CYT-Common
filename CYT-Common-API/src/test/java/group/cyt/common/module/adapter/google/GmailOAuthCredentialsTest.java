package group.cyt.common.module.adapter.google;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.credential.service.CredentialService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GmailOAuthCredentialsTest {
  CredentialService credentials = mock(CredentialService.class);
  GatewaySettings settings = mock(GatewaySettings.class);
  GmailOAuthCredentials subject = spy(new GmailOAuthCredentials(credentials, settings));
  String key = GmailOAuthCredentials.mailboxKey("epomaker", "support@epomaker.com");
  String version = "projects/634741927064/secrets/CYT_CS_GMAIL_OAUTH_" + key + "/versions/2";
  Map<String, Object> bundle =
      Map.of(
          "system",
          "epomaker",
          "account",
          "support@epomaker.com",
          "client_id",
          "id",
          "client_secret",
          "secret",
          "refresh_token",
          "refresh");

  @BeforeEach
  void setup() {
    when(settings.get("GMAIL_OAUTH_SECRET_PROJECT")).thenReturn("634741927064");
    when(credentials.raw(anyString())).thenReturn("");
    doReturn(bundle).when(subject).readVersion(anyString());
  }

  void bind(String account, String reference) {
    when(credentials.raw("GMAIL_OAUTH_" + key + "_BINDING"))
        .thenReturn(
            JsonUtils.write(
                Map.of("system", "epomaker", "account", account, "version", reference)));
  }

  @Test
  void absentBindingKeepsExistingConfiguration() {
    assertTrue(subject.resolve("epomaker", "support@epomaker.com").isEmpty());
    verify(subject, never()).readVersion(anyString());
  }

  @Test
  void validFixedVersionLoadsOnceAndRotationUsesNewVersion() {
    bind("support@epomaker.com", version);
    assertEquals(bundle, subject.resolve("epomaker", "support@epomaker.com"));
    subject.resolve("epomaker", "support@epomaker.com");
    verify(subject, times(1)).readVersion(version);
    String next = version.replace("/versions/2", "/versions/3");
    bind("support@epomaker.com", next);
    subject.resolve("epomaker", "support@epomaker.com");
    verify(subject).readVersion(next);
  }

  @Test
  void rejectsOtherMailboxAndMutableOrUnrelatedSecretReferences() {
    bind("other@epomaker.com", version);
    assertThrows(GatewayException.class, () -> subject.resolve("epomaker", "support@epomaker.com"));
    for (String ref :
        new String[] {
          version.replace("/2", "/latest"),
          version.replace(key, "OTHER"),
          version.replace("634741927064", "12345")
        }) {
      bind("support@epomaker.com", ref);
      assertThrows(
          GatewayException.class, () -> subject.resolve("epomaker", "support@epomaker.com"));
    }
    verify(subject, never()).readVersion(anyString());
  }

  @Test
  void rejectsMismatchedPayloadAndDoesNotFallBackOnReadFailure() {
    bind("support@epomaker.com", version);
    doReturn(Map.of("system", "cs", "account", "support@epomaker.com"))
        .when(subject)
        .readVersion(version);
    assertThrows(GatewayException.class, () -> subject.resolve("epomaker", "support@epomaker.com"));
    var failed = spy(new GmailOAuthCredentials(credentials, settings));
    doThrow(GatewayException.config("unavailable")).when(failed).readVersion(version);
    assertThrows(GatewayException.class, () -> failed.resolve("epomaker", "support@epomaker.com"));
  }

  @Test
  void fullMailboxAndCallingSystemHaveSeparateKeys() {
    assertEquals(key, GmailOAuthCredentials.mailboxKey("EPOMAKER", " SUPPORT@EPOMAKER.COM "));
    assertNotEquals(key, GmailOAuthCredentials.mailboxKey("epomaker", "sales@epomaker.com"));
    assertNotEquals(key, GmailOAuthCredentials.mailboxKey("cs", "support@epomaker.com"));
  }
}
