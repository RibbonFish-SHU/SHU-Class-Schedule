package io.github.zmdld11.shuschedule.data.jwxk

/**
 * 上大正方教务（jwxt.shu.edu.cn）导入契约。
 *
 * 设计原则（issue #3 两轮真机反馈的结论）：
 * - WebView 入口必须是 SSO 网关 /sso/shulogin：它会 302 到 newsso.shu.edu.cn 的 OAuth2
 *   统一身份认证（上大账号唯一可登录处），登录后带 code 落回 jwxt 域建立会话。
 *   根路径与业务深路径都不行——深路径未登录会被带去正方官方登录页（上大账号无凭据，
 *   即 Sleepy 翻车点）。
 * - 登录后不主动跳转任何深页：课表数据用已登录页面的同源 fetch 直接 POST 获得即可。
 */
object JwxkSpec {

    const val BASE_URL = "https://jwxt.shu.edu.cn"

    /** 上大统一身份认证网关：302 → newsso.shu.edu.cn/oauth/authorize（表单 JS 渲染） */
    const val SSO_LOGIN_URL = "https://jwxt.shu.edu.cn/sso/shulogin"

    /** 个人课表查询（POST，gnmkdm=N2151），xnm=学年起始年，xqm=学期编码（上大需探测） */
    private const val SCHEDULE_API = "/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151"

    /**
     * 登录态探测脚本：同源 POST 课表接口，返回 JSON（无论 kbList 是否为空）即视为已登录；
     * 被 302 带回登录页（content-type text/html 或 resp.redirected）视为未登录。
     */
    fun loginProbeScript(bridge: String): String = """
        (async function() {
          try {
            var resp = await fetch('$SCHEDULE_API', {
              method: 'POST',
              headers: {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest'},
              body: 'xnm=2026&xqm=3&kzlx=ck&xsdm=&kclbdm=&kclxdm=',
              credentials: 'include'
            });
            var ct = (resp.headers.get('content-type') || '').toLowerCase();
            var text = await resp.text();
            var looksJson = ct.indexOf('json') >= 0 || text.replace(/^\s+/, '').charAt(0) === '{';
            var ok = looksJson && !resp.redirected;
            $bridge.onLoginProbe(ok);
          } catch (e) { $bridge.onLoginProbe(false); }
        })();
    """.trimIndent()

    /**
     * 抓取脚本：按候选 xqm 顺序探测，返回首个 kbList 非空的学期数据；
     * 单候选也接受空 kbList（学期确实无课）。结果经 bridge.onScheduleJson 回传。
     */
    fun fetchScheduleScript(bridge: String, xnm: Int, xqmCandidates: List<Int>): String {
        val candidates = xqmCandidates.joinToString(prefix = "[", postfix = "]") { "$it" }
        return """
            (async function() {
              var CANDIDATES = $candidates, XNM = $xnm;
              async function tryFetch(xqm) {
                var body = 'xnm=' + XNM + '&xqm=' + xqm + '&kzlx=ck&xsdm=&kclbdm=&kclxdm=';
                var resp = await fetch('$SCHEDULE_API', {
                  method: 'POST',
                  headers: {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest'},
                  body: body,
                  credentials: 'include'
                });
                var text = await resp.text();
                var data = null;
                try { data = JSON.parse(text); } catch (e) {}
                if (!data || !Array.isArray(data.kbList)) return {login: false};
                return {login: true, data: data};
              }
              var firstOk = null;
              for (var i = 0; i < CANDIDATES.length; i++) {
                var r;
                try { r = await tryFetch(CANDIDATES[i]); }
                catch (e) { $bridge.onScheduleJson(JSON.stringify({ok: false, error: 'network'})); return; }
                if (!r.login) { $bridge.onScheduleJson(JSON.stringify({ok: false, error: 'login'})); return; }
                if (r.data.kbList.length > 0) {
                  $bridge.onScheduleJson(JSON.stringify({ok: true, xqm: CANDIDATES[i], payload: r.data}));
                  return;
                }
                if (firstOk === null) firstOk = r.data;
              }
              $bridge.onScheduleJson(JSON.stringify({ok: true, xqm: CANDIDATES[0], payload: firstOk, empty: true}));
            })();
        """.trimIndent()
    }
}
