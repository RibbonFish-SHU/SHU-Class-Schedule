package io.github.zmdld11.shuschedule.data.jwxk

/**
 * 上大正方教务（jwxt.shu.edu.cn）导入契约。
 *
 * 设计原则（针对 Sleepy 在上大翻车的深路径/会话问题）：
 * - WebView 只打开根路径/登录页，用户手动登录，全程不跳转业务深链
 * - 数据获取一律在已登录页面的 JS 上下文里用同源 fetch，Cookie/Referer 自动携带
 *   （该路线与 SHU-jwxk-assistant 的直连 POST 等价，已长期验证）
 */
object JwxkSpec {

    const val BASE_URL = "https://jwxt.shu.edu.cn"

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
