package group.cyt.common.module.callback.controller;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.module.callback.service.PaymentDisputeEvents;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentDisputeCallbackController {
  private final PaymentDisputeEvents events;

  public PaymentDisputeCallbackController(PaymentDisputeEvents events) {
    this.events = events;
  }

  @PostMapping("/integrations/{provider:stripe|paypal}/webhooks/{account}/dispute-closed")
  public Object receive(@PathVariable String provider, @PathVariable String account,
      HttpServletRequest request) throws IOException {
    int max = 1024 * 1024;
    if (request.getContentLengthLong() > max) throw GatewayException.invalid("支付回调内容过大");
    return events.receive(provider, account, request.getInputStream().readNBytes(max + 1), request);
  }
}
