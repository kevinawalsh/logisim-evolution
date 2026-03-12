const _h = window.location.hostname;
// const isApp = (_h === '127.0.0.1' || _h === 'localhost' || _h === '[::1]');
const isApp = (_h === '127.0.0.1' || _h === '[::1]');

// window location should be "/logisim-evolution/en/..." or similar
let parts = window.location.pathname.split('/');
let lang = (parts[2] && parts[2].length === 2) ? parts[2] : 'en';


function escapeHTML(str) {
    return new Option(str).innerHTML;
}

// open navigation sidebar
function openNav() {
  localStorage.setItem("sidebar", "open");
  // document.getElementById("mySidebar").style.width = "200px";
  // document.getElementById("mySidebar").style.overflowY = "auto"; // show scrollbar
  document.getElementById("mySidebar").style.left = "0px";
  document.getElementById("main").style.marginLeft = "200px";
}

// close navigation sidebar
function closeNav() {
  localStorage.setItem("sidebar", "closed");
  // document.getElementById("mySidebar").style.overflowY = "hidden"; // hide scrollbar
  // document.getElementById("mySidebar").style.width = "0px";
  document.getElementById("mySidebar").style.left = "-240px";
  document.getElementById("main").style.marginLeft= "30px";
}

let miniSearch = null;
let suggest = null;
let curSuggest = -1;
let origSuggest = null;

function doSuggest(i, search) {
    if (!suggest || !miniSearch)
        return false;

    if (i < 0)
        i = -1;
    else if (i >= suggest.length)
        i = suggest.length - 1;

    if (i == curSuggest)
        return false;

    let q = document.getElementById("query");

    // deactivate prevous selection
    if (curSuggest < 0) {
        origSuggest = q.value;
    } else {
        let si = document.getElementById("suggestion_" + curSuggest);
        if (si)
            si.className = 'suggestion';
    }

    // activate new selection
    curSuggest = i;
    if (i < 0) {
        q.value = origSuggest;
    } else {
        q.value = suggest[curSuggest].suggestion;
        let si = document.getElementById("suggestion_" + curSuggest);
        if (si)
            si.className = 'activesuggestion';
    }
    if (search)
        doSearch(null);

    return false;
}

function clearSuggest() {
    let ss = document.getElementById("suggest");
    if (ss)
        ss.remove();
    curSuggest = -1;
    suggest = null;
    origSuggest = null;
    localStorage.removeItem("suggest");
}

function keydown(event) {
    if (!suggest || !miniSearch)
        return false;

    if (event.key === 'ArrowDown') {
        event.preventDefault();
        doSuggest(curSuggest + 1, false);
    } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        doSuggest(curSuggest - 1, false);
    } else if (event.key === 'Escape') {
        event.preventDefault();
        clearSuggest();
    }
    return false;
}

function hideSearch() {
    clearSuggest();
    localStorage.removeItem("query");
    let sr = document.getElementById("results");
    if (sr) {
        sr.style.visibility = "hidden";
        sr.innerHTML = "";
    }
}

function doSearch(event) {
    if (event)
        event.preventDefault();
    clearSuggest();
    showSearch();
    return false;
}

function showSearch() {
    let q = document.getElementById("query");
    if (!q || !miniSearch)
        return;
    let query = q.value;
    localStorage.setItem("query", query);
    var results = miniSearch.search(query);
    var n = results.length;
    var sr = document.getElementById("results");
    sr.innerHTML = 'Found ' + n + ' results.<div id="closesearch"><a href="javascript:void(0)" title="Hide Search Results" onclick="hideSearch()">&#10006;</a></div>';
    for (var i = 0; i < n; i++) {
        sr.innerHTML += '<br>('+(i+1)+') <a href="' + results[i].url + '">' + results[i].title + '</a>';
    }
    sr.style.visibility = "visible";
}
    

function doIncremental(event) {
    var q = document.getElementById("query");
    if (!q || !miniSearch)
        return;
    var query = q.value;
    suggest = miniSearch.autoSuggest(query, {
                prefix: term => term.length > 2,
                fuzzy: term => term.length > 3 ? 0.2 : null
            });
    curSuggest = -1;
    origSuggest = query;
    var ss = document.getElementById("suggest");
    if (!ss) {
        ss = document.createElement("div");
        ss.id = "suggest";
        q.parentNode.appendChild(ss); // insertBefore(ss, query.nextSibling)
    }
    // get rid of some silly suggestions, and truncate to top 5 suggestions
    for (var i = 0; i < 5 && i < suggest.length; i++) {
        if (suggest[i].suggestion === query) {
            suggest.splice(i, 1);
            i--;
        }
    }
    if (suggest.length > 5)
        suggest = suggest.slice(0, 5);
    if (suggest.length > 0) {
        localStorage.setItem("suggest", "open");
        var suggestions = "";
        for (var i = 0; i < suggest.length; i++) {
            /* if (i > 0)
                    suggestions += "<br>"; */
            suggestions += '<span class="suggestion" id="suggestion_'+i+'" onclick="doSuggest('+i+', true)">' + suggest[i].score + " " + escapeHTML(suggest[i].suggestion) + '</span>';
        }
        ss.innerHTML = suggestions;
        ss.style.visibility = "visible";
    } else {
        clearSuggest();
    }

    showSearch();
    return false;
}

function live(e, el) {
  e.preventDefault();

  if (!isApp) {
    showLiveInfo(el, false);
    return;
  }

  fetch(el.href, { method: 'GET' })
    .then(r => {
      if (!r.ok) throw new Error("Server error");
      el.classList.add('success');
      setTimeout(() => el.classList.remove('success'), 1500);
    })
    .catch(() => showLiveInfo(el, true));
}

function showLiveInfo(el, appNotRunning) {
  const params = new URL(el.href).searchParams;
  const name = escapeHTML((params.get('file') || '').replace(/\.circ$/, ''));

  const old = document.getElementById('logisim-live-info');
  if (old) old.remove();

  const popup = document.createElement('div');
  popup.id = 'logisim-live-info';
  const W = 300;
  const r = el.getBoundingClientRect();
  const left = Math.min(r.left, window.innerWidth - W - 16);
  const top = r.bottom + 8;
  popup.style.cssText = 'position:fixed;top:' + top + 'px;left:' + Math.max(8, left) + 'px;width:' + W + 'px;'
    + 'background:#fff;border:1px solid #bbb;border-radius:6px;'
    + 'box-shadow:0 4px 16px rgba(0,0,0,.25);padding:12px 36px 12px 14px;'
    + 'font-size:13px;line-height:1.5;z-index:9999;';

  let html = '';
  if (appNotRunning)
    html += '<p style="margin:0 0 6px"><b>Could not reach Logisim</b> \u2014 is it running?</p>';
  html += '<p style="margin:0' + (!appNotRunning ? ' 0 6px' : '') + '">'
        + 'This demo is in Logisim under <b>File \u2192 Examples \u2192 ' + name + '</b>.</p>';
  if (!appNotRunning)
    html += '<p style="margin:6px 0 0"><a href="https://github.com/kevinawalsh/logisim-evolution/releases" target="_blank" rel="noopener">'
          + 'Download Logisim</a></p>';
  html += '<button onclick="document.getElementById(\'logisim-live-info\').remove()"'
        + ' title="Dismiss"'
        + ' style="position:absolute;top:6px;right:8px;border:none;background:none;'
        + 'font-size:18px;line-height:1;cursor:pointer;color:#888;padding:0;">&times;</button>';

  popup.innerHTML = html;
  document.body.appendChild(popup);

  function onOutside(e) {
    const p = document.getElementById('logisim-live-info');
    if (p && !p.contains(e.target)) {
      p.remove();
      document.removeEventListener('mousedown', onOutside, true);
    }
  }
  setTimeout(() => document.addEventListener('mousedown', onOutside, true), 0);
}

window.onload = function() {

  if (!isApp) {
    const banner = document.createElement('div');
    banner.id = 'logisim-banner';
    banner.innerHTML = 'Logisim-Evolution documentation, version 5.1.1-HC.'
      + ' <a href="https://github.com/kevinawalsh/logisim-evolution">Source on GitHub</a>.';
    document.body.prepend(banner);
  }

  function loadStyle(url) {
    return new Promise((resolve, reject) => {
      let link = document.createElement('link');
      link.type = 'text/css';
      link.rel = 'stylesheet';
      link.onload = () => { resolve(); }
      link.href = url;
      let headScript = document.querySelector('script');
      headScript.parentNode.append(link);
    });
  }

  function loadScript(url, callback) {
    let head = document.head;
    let script = document.createElement('script');
    script.type = 'text/javascript';
    script.src = url;
    script.onreadystatechange = callback;
    script.onload = callback;
    head.appendChild(script);
  }

  // fetch sidebar data and inject sidebar into body
  async function loadSidebar() {
    const response = await fetch("/logisim-evolution/"+lang+"/sidebar.html");
    const sidebar_list = await response.text();

    // sidebar.html may contain relative urls, adjust them here
    const tempDiv = document.createElement('div');
    tempDiv.innerHTML = sidebar_list;
    const base = "/logisim-evolution/" + lang + "/";
    // Fix href attributes
    tempDiv.querySelectorAll('a[href]').forEach(a => {
      const h = a.getAttribute('href');
      if (h && !h.startsWith('/') && !h.match(/^[a-z]+:/))
        a.setAttribute('href', base + h);
    });
    // Fix list-style-image: url(...) in style attributes
    // Note: all sidebar list-style-image urls are currently absolute paths,
    // so this step isn't needed now, but it may be in future.
    tempDiv.querySelectorAll('[style]').forEach(el => {
      el.setAttribute('style', el.getAttribute('style').replace(
        /url\((['"]?)(?!\/|[a-z]+:)(.*?)\1\)/g,
          (_, q, p) => `url(${q}${base}${p}${q})`
        ));
    });

        const body = document.body.innerHTML;
        document.body.innerHTML = 
        '<div id="myCollapsedBar" class="collapsedbar">'
        + '<a href="javascript:void(0)" title="Open Menu" class="menu" onclick="openNav()"></a>'
        + '</div>'
        + '<div id="mySidebar" class="sidebar">'
        + '<a href="javascript:void(0)" id="closebtn" class="btn" onclick="closeNav()">&lt</a>'
        + tempDiv.innerHTML
        + '</div>'
        + '<div id="search"></div>'
        + '<div id="main">' + body + ' </div>';

        let fs = localStorage.getItem("sidebar");
        if (fs === "closed")
        closeNav()

        let sidebar = document.getElementById("mySidebar"); // document.querySelector(".sidebar");
        let top = localStorage.getItem("sidebar-scroll");
        if (top)
        sidebar.scrollTop = parseInt(top, 10);
        highlightCurrentPage();

        window.addEventListener("beforeunload", () => {
          localStorage.setItem("sidebar-scroll", sidebar.scrollTop);
        });

  }

  function highlightCurrentPage() {
    const currentPath = normalizePath(window.location.pathname);
    const sidebar = document.getElementById("mySidebar");
    if (!sidebar) return;

    // Find the anchor whose href path matches the current page
    const links = sidebar.querySelectorAll("a");
    let active = null;
    for (const link of links) {
      const url = new URL(link.href);
      if (normalizePath(url.pathname) === currentPath) {
        active = link;
        break;
      }
    }
    if (!active) return;

    // Highlight it
    active.classList.add("sidebar-current");

    // Scroll the sidebar so the link is visible, roughly centered
    const sidebarRect = sidebar.getBoundingClientRect();
    const linkRect = active.getBoundingClientRect();
    const offset = linkRect.top - sidebarRect.top - (sidebar.clientHeight / 2);
    sidebar.scrollTop = sidebar.scrollTop + offset;
  }

  function normalizePath(path) {
    if (path.endsWith("/index.html"))
      return path.slice(0, -"index.html".length); // "somedir/index.html" -> "somedir/"
    if (!path.endsWith("/") && !path.includes("."))
      return path + "/";                           // "somedir" -> "somedir/"
    return path;                                     // "somedir/" or "tutor-gates.html" unchanged
  }

  function restoreSearchState() {
    let searchQuery = localStorage.getItem("query");
    if (!searchQuery)
      return;
    let q = document.getElementById("query");
    q.value = searchQuery;

    let suggestState = localStorage.getItem("suggest");
    if (suggestState === "open") {
      doIncremental(null);
    } else {
      showSearch();
    }
  }

  async function fetchSearchInfo() {
    const response = await fetch("/logisim-evolution/"+lang+"/contents.json");
    const documents = await response.json();

    miniSearch.addAll(documents);

    let search = document.getElementById("search");
    if (!search)
      return;
    search.innerHTML = '<form autocomplete="off" onsubmit="doSearch(event);">'
      + '<input type="submit" value="Search">'
      + '<span>'
      + '<input type="text" id="query" name="query" onkeydown="keydown(event)" oninput="doIncremental(event)"/>'
      + '</span>'
      + '<div id="results"></div>'
      + '</form>';
    restoreSearchState();
  }

  function setupSearch() {
    miniSearch = new MiniSearch({
      fields: ['title', 'text'], // fields to index for full-text search
      storeFields: ['title', 'url', ], // fields to return with search results
      searchOptions: {
        prefix: term => term.length > 2,
        fuzzy: term => term.length > 3 ? 0.2 : null
      }
    });
    fetchSearchInfo();
  }

  async function initialize() {
    await loadStyle("/logisim-evolution/sidebar.css");
    await loadSidebar();
    loadScript("/logisim-evolution/minisearch-6.1.0.min.js", setupSearch);
  }

  initialize();
};
