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

    /**
     * 课表查询页（含学年/学期下拉框）。登录态建立后导航到此页，
     * 从活 DOM 读取教务自己的 xnm/xqm 真实编码（上大改版后编码非标且会变，
     * jwxk .env 实证夏季=32、秋季=3，常规 12/16 不可靠）。
     */
    const val INDEX_URL =
        "https://jwxt.shu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"

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

    /**
     * 学期下拉采集脚本：在课表查询页的活 DOM 里找学年（xnm）/学期（xqm）两个 select，
     * 读出全部选项（value=真实编码）。select 定位两段兜底：
     * ① id/name 含 xnm / xqm；② 启发式——选项值全为 19xx/20xx 视为学年、选项含「X季」视为学期。
     * 找不到时上报 ok=false（页面 JS 可能还没把下拉渲染出来，调用方轮询重试）。
     */
    fun semesterHarvestScript(bridge: String): String = """
        (function() {
          function readSel(sel) {
            var out = [];
            for (var i = 0; i < sel.options.length; i++) {
              var o = sel.options[i];
              out.push({value: String(o.value == null ? '' : o.value).trim(),
                        label: String(o.textContent == null ? '' : o.textContent).trim(),
                        selected: !!o.selected});
            }
            return out;
          }
          var sels = Array.prototype.slice.call(document.querySelectorAll('select'));
          var yearSel = null, termSel = null;
          sels.forEach(function(s) {
            var key = ((s.id || '') + ' ' + (s.name || '')).toLowerCase();
            if (!yearSel && key.indexOf('xnm') >= 0) yearSel = s;
            if (!termSel && key.indexOf('xqm') >= 0) termSel = s;
          });
          if (!yearSel) {
            sels.forEach(function(s) {
              if (yearSel) return;
              var os = Array.prototype.slice.call(s.options).filter(function(o) {
                return String(o.value == null ? '' : o.value).trim() !== '';
              });
              if (os.length >= 2 && os.every(function(o) { return /^(19|20)\d{2}$/.test(String(o.value).trim()); })) yearSel = s;
            });
          }
          if (!termSel) {
            sels.forEach(function(s) {
              if (termSel || s === yearSel) return;
              var os = Array.prototype.slice.call(s.options);
              if (os.some(function(o) { return /(秋|冬|春|夏)季/.test(String(o.textContent || '')); })) termSel = s;
            });
          }
          if (yearSel && termSel) {
            $bridge.onSemesterJson(JSON.stringify({ok: true, years: readSel(yearSel), terms: readSel(termSel)}));
          } else {
            $bridge.onSemesterJson(JSON.stringify({ok: false}));
          }
        })();
    """.trimIndent()
}
