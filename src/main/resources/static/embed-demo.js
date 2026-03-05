(function (global) {
  function createClient(options) {
    const opts = options || {};
    const frameId = opts.frameId || 'chatbotFrame';
    const frame = document.getElementById(frameId);
    const frameSrc = (frame && (frame.getAttribute('src') || frame.src)) || '/chat';
    const resolvedFrameOrigin = new URL(frameSrc, window.location.href).origin;
    const targetOrigin = opts.targetOrigin || resolvedFrameOrigin;

    if (!frame) {
      throw new Error('chatApp: iframe not found (' + frameId + ')');
    }

    const wrap = document.getElementById(opts.wrapId || 'chatbotWrap');
    const bar = document.getElementById(opts.barId || 'chatbotBar');
    const body = document.getElementById(opts.bodyId || 'chatbotBody');
    const btnMin = document.getElementById(opts.btnMinId || 'btnMin');
    const btnMax = document.getElementById(opts.btnMaxId || 'btnMax');

    let ragContext = '';
    let user = '';
    let minimized = false;
    let maximized = false;

    const defaultBox = {
      width: 420,
      bodyHeight: 780,
      top: 24,
      left: 24
    };

    function postContext() {
      if (!frame.contentWindow) return;
      frame.contentWindow.postMessage(
        {
          type: 'CHATBOT_SET_CONTEXT',
          ragContext: ragContext,
          user: user
        },
        targetOrigin
      );
    }

    frame.addEventListener('load', function () {
      postContext();
      setTimeout(postContext, 500);
    });

    window.addEventListener('message', function (event) {
      if (event.origin !== targetOrigin) return;
      if (event.data && event.data.type === 'CHATBOT_READY') {
        postContext();
      }
      if (event.data && event.data.type === 'CHATBOT_CONTEXT_APPLIED') {
        console.log('chatbot context applied');
      }
    });

    function applyDefaultSize() {
      if (!wrap || !body || !frame) return;
      wrap.style.width = defaultBox.width + 'px';
      body.style.height = defaultBox.bodyHeight + 'px';
      frame.width = String(defaultBox.width);
      frame.height = String(defaultBox.bodyHeight);
      frame.style.width = defaultBox.width + 'px';
      frame.style.height = defaultBox.bodyHeight + 'px';
    }

    function setMinimized(next) {
      if (!body || !btnMin) return;
      minimized = !!next;
      body.style.display = minimized ? 'none' : 'block';
      btnMin.textContent = minimized ? '복원' : '최소화';
    }

    function setMaximized(next) {
      if (!wrap || !body || !frame || !btnMax) return;
      maximized = !!next;

      if (maximized) {
        wrap.style.top = '0';
        wrap.style.left = '0';
        wrap.style.width = '100vw';
        wrap.style.height = '100vh';
        body.style.display = 'block';
        body.style.height = 'calc(100vh - 44px)';
        frame.style.width = '100vw';
        frame.style.height = 'calc(100vh - 44px)';
        btnMax.textContent = '기본크기';
        setMinimized(false);
      } else {
        wrap.style.height = '';
        wrap.style.top = defaultBox.top + 'px';
        wrap.style.left = defaultBox.left + 'px';
        applyDefaultSize();
        btnMax.textContent = '최대화';
      }
    }

    if (btnMin) {
      btnMin.addEventListener('click', function () {
        if (maximized) setMaximized(false);
        setMinimized(!minimized);
      });
    }

    if (btnMax) {
      btnMax.addEventListener('click', function () {
        setMaximized(!maximized);
      });
    }

    if (wrap && bar) {
      let dragging = false;
      let startX = 0;
      let startY = 0;
      let baseLeft = 0;
      let baseTop = 0;

      bar.addEventListener('mousedown', function (e) {
        if (maximized) return;
        if (e.target && e.target.tagName === 'BUTTON') return;
        dragging = true;
        startX = e.clientX;
        startY = e.clientY;
        baseLeft = wrap.offsetLeft;
        baseTop = wrap.offsetTop;
        document.body.style.userSelect = 'none';
      });

      window.addEventListener('mousemove', function (e) {
        if (!dragging) return;
        const nextLeft = baseLeft + (e.clientX - startX);
        const nextTop = baseTop + (e.clientY - startY);
        wrap.style.left = Math.max(0, nextLeft) + 'px';
        wrap.style.top = Math.max(0, nextTop) + 'px';
      });

      window.addEventListener('mouseup', function () {
        if (!dragging) return;
        dragging = false;
        document.body.style.userSelect = '';
      });
    }

    applyDefaultSize();


    return {
      setRagContext: function (nextRagContext) {
        if (typeof nextRagContext === 'string') {
          ragContext = nextRagContext;
        } else if (nextRagContext && typeof nextRagContext === 'object') {
          ragContext = JSON.stringify(nextRagContext);
        } else {
          throw new Error('chatApp: ragContext must be a string or object');
        }
        postContext();
      },
      setUser: function (nextUser) {
        if (typeof nextUser === 'string') {
          user = nextUser;
        } else if (nextUser && typeof nextUser === 'object' && typeof nextUser.name === 'string') {
          user = nextUser.name;
        } else {
          throw new Error('chatApp: user must be a string or { name: string }');
        }
        postContext();
      },
      minimize: function () { setMinimized(true); },
      restore: function () { setMinimized(false); setMaximized(false); },
      maximize: function () { setMaximized(true); }
    };
  }

  global.chatApp = {
    init: function (options) {
      return createClient(options);
    }
  };
})(window);
