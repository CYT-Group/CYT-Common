package group.cyt.common.module.adapter;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.module.adapter.support.BusinessWorkflows;
import group.cyt.common.module.adapter.wecom.approval.WecomApprovalService;
import group.cyt.common.module.registry.model.OperationDefinition;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AdapterDispatcher {
  private final List<PlatformAdapter> adapters;
  private final MockAdapter mock;
  private final GatewaySettings settings;
  private final OperationRegistry registry;
  private final WecomApprovalService approvals;
  private final BusinessWorkflows workflows;

  public AdapterDispatcher(
      List<PlatformAdapter> adapters,
      MockAdapter mock,
      GatewaySettings settings,
      OperationRegistry registry,
      WecomApprovalService approvals,
      BusinessWorkflows workflows) {
    this.adapters = adapters;
    this.mock = mock;
    this.settings = settings;
    this.registry = registry;
    this.approvals = approvals;
    this.workflows = workflows;
  }

  /**
   * 模拟态按 <PLATFORM>_<RESOURCE>_MOCK、<PLATFORM>_MOCK 的顺序取值，两者都未显式配置时回落到 MOCK_ALL；
   * 这样可以在保持其余平台模拟的同时单独放开审批这类资源走真连。
   */
  public boolean mock(OperationDefinition op) {
    // Upload never inherits the local default MOCK_ALL=true or a resource-level switch.
    if (op.rawBinary()) return settings.flag("NAS_MOCK", settings.flag("MOCK_ALL", false));
    if (op.platform().equals("discord")) return false;
    if (!registry.builtin(op.key())) return false;
    for (String flag : mockFlags(op)) if (settings.isSet(flag)) return settings.flag(flag, false);
    return settings.mock();
  }

  /** 该操作的模拟开关名，按优先级排列；生产环境用同一份口径拒绝任何被打开的模拟开关。 */
  public static List<String> mockFlags(OperationDefinition op) {
    String platform = op.platform().toUpperCase(Locale.ROOT);
    String[] segments = op.key().split("\\.");
    return segments.length > 2
        ? List.of(
            platform + "_" + segments[1].toUpperCase(Locale.ROOT) + "_MOCK", platform + "_MOCK")
        : List.of(platform + "_MOCK");
  }

  public String environment() {
    return settings.current();
  }

  public Object invoke(OperationDefinition op, Map<String, Object> params, String system) {
    if (op.rawBinary()) throw GatewayException.invalid("此操作需要 PUT 原始文件流");
    if (approvals.supports(op.key())) return approvals.invoke(op, params, system, mock(op));
    if (workflows.supports(op.key())) return workflows.invoke(op, params, system, mock(op));
    if (mock(op)) return mock.invoke(op, params, system);
    return adapters.stream()
        .filter(
            a -> a.supports(op.platform()) || a.supports("custom") && !registry.builtin(op.key()))
        .findFirst()
        .orElseThrow(() -> GatewayException.config("平台适配器未加载: " + op.platform()))
        .invoke(op, params, system);
  }

  public Object upload(
      OperationDefinition op,
      Map<String, Object> params,
      String system,
      group.cyt.common.core.http.BinaryUpload source) {
    if (!op.key().equals("nas.objects.upload")) throw GatewayException.invalid("不支持的二进制操作");
    if (settings.production() && mock(op)) throw GatewayException.config("生产环境禁止模拟上传");
    return adapters.stream()
        .filter(a -> a instanceof group.cyt.common.module.adapter.nas.NasAdapter)
        .map(a -> (group.cyt.common.module.adapter.nas.NasAdapter) a)
        .findFirst()
        .orElseThrow(() -> GatewayException.config("NAS 适配器未加载"))
        .upload(params, system, source, mock(op));
  }
}
