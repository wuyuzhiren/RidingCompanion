/* 骑行小智 Live2D 虚拟形象渲染驱动
 * 基于 pixi-live2d-display + Live2D Cubism Core
 * 提供供 Android 侧调用的全局接口：
 *   Live2D_setMouth(v)    说话口型，v=0~1 音量
 *   Live2D_setSpeaking(b)  说话状态（点头）
 *   Live2D_react()        点击互动反应
 *   Live2D_setEyeBlink(b)  开/关自动眨眼
 */
(function () {
  'use strict';

  var app = null;
  var model = null;
  var speaking = false;
  var mouthTarget = 0;
  var mouthCur = 0;
  var reactUntil = 0;
  var eyeBlinkEnabled = true;
  var loaded = false;

  function setParam(id, value) {
    if (!model || !model.internalModel || !model.internalModel.coreModel) return;
    try {
      model.internalModel.coreModel.setParameterValueById(id, value);
    } catch (e) {}
  }

  function getParam(id) {
    if (!model || !model.internalModel || !model.internalModel.coreModel) return 0;
    try {
      return model.internalModel.coreModel.getParameterValueById(id);
    } catch (e) { return 0; }
  }

  function fitModel() {
    if (!model || !app) return;
    var w = app.screen.width;
    var h = app.screen.height;
    var mw = model.width || 2;
    var mh = model.height || 2;
    var scale = Math.min(w / mw, h / mh);
    if (h / w > 1.2) {
      // 竖屏：让人物更靠上，占主体
      scale = Math.min(w / mw * 1.05, h / mh * 1.0);
    }
    model.scale.set(scale);
    model.anchor.set(0.5, 1.0);
    model.x = w / 2;
    model.y = h;
    // 稍微下移避免贴顶
    model.y = h - Math.max(0, (h - mh * scale) * 0.15);
  }

  function boot() {
    if (loaded) return;
    loaded = true;
    var canvas = document.getElementById('canvas');
    app = new PIXI.Application({
      view: canvas,
      transparent: true,
      backgroundAlpha: 0,
      autoStart: true,
      antialias: true,
      autoDensity: true,
      resolution: Math.min(window.devicePixelRatio || 1, 2),
      width: window.innerWidth,
      height: window.innerHeight
    });
    window.addEventListener('resize', function () {
      if (!app) return;
      app.renderer.resize(window.innerWidth, window.innerHeight);
      fitModel();
    });

    PIXI.live2d.Live2DModel.from('Hiyori.model3.json', { autoInteract: true })
      .then(function (m) {
        model = m;
        model.autoUpdate = true;
        model.autoInteract = true;
        window.__model = m; // 调试用
        app.stage.addChild(model);
        fitModel();
        // 点击身体互动
        model.on('hit', function (hitAreas) {
          if (hitAreas && hitAreas.length > 0) {
            react();
          }
        });
        // 引擎就绪后通知宿主
        if (window.Live2D_ready) { try { window.Live2D_ready(); } catch (e) {} }
      })
      .catch(function (err) {
        // eslint-disable-next-line no-console
        console.error('Live2D load error:', err);
      });

    // 每帧：口型平滑 + 说话点头 + 呼吸 + 互动表情
    app.ticker.add(function () {
      if (!model) return;
      var t = Date.now() / 1000;
      // 口型平滑跟随（直接驱动 ParamMouthOpenY 参数）
      mouthCur += (mouthTarget - mouthCur) * 0.28;
      if (Math.abs(mouthCur) < 0.004) mouthCur = 0;
      setParam('ParamMouthOpenY', mouthCur);
      // 呼吸（叠加到 Idle 动画之上）
      var breath = 0.5 + 0.5 * Math.sin(t * 1.2);
      setParam('ParamBreath', breath);
      // 说话点头/摇头微动
      if (speaking) {
        setParam('ParamAngleZ', Math.sin(t * 5.2) * 2.2);
        setParam('ParamAngleX', Math.sin(t * 3.1) * 1.6);
      } else {
        setParam('ParamAngleZ', 0);
        setParam('ParamAngleX', 0);
      }
      // 互动表情期间：歪头、眯眼微笑
      if (reactUntil > 0 && t < reactUntil) {
        setParam('ParamAngleZ', 10);
        setParam('ParamEyeLOpen', 0.55);
        setParam('ParamEyeROpen', 0.55);
      }
    });
  }

  function react() {
    if (!model || !app) return;
    var now = Date.now() / 1000;
    reactUntil = now + 1.6;
    try {
      model.internalModel.motionManager.startRandomMotion('TapBody');
    } catch (e) {}
  }

  // ---------- Android 桥接接口 ----------
  window.Live2D_setMouth = function (v) {
    v = parseFloat(v);
    mouthTarget = isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
  };
  window.Live2D_setSpeaking = function (b) {
    speaking = (b === true || b === 'true' || b === 1 || b === '1');
  };
  window.Live2D_react = function () { react(); };
  window.Live2D_getMouth = function () { return String(mouthCur); };
  window.Live2D_setEyeBlink = function (b) {
    eyeBlinkEnabled = (b === true || b === 'true' || b === 1 || b === '1');
    if (model) { model.internalModel.eyeBlinkEnabled = eyeBlinkEnabled; }
  };

  // 页面加载后启动
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
