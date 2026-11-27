package com.autopilot.android.feature.scripting

/** Opt-in randomized scroll/click behaviour, injected after each page load. */
object AutoHumanBehaviourScript {
    val SCRIPT: String = """
(function() {
  // Prevent the script from running multiple times if injected repeatedly
  if (window.__autoBotRunning) return;
  window.__autoBotRunning = true;

  // --- CONFIGURATION ---
  const CONFIG = {
    minDelayMs: 2000,       // Minimum wait between actions (2 seconds)
    maxDelayMs: 5000,       // Maximum wait between actions (5 seconds)
    actionsBeforeReload: 8, // Number of scroll/click actions before refreshing
    scrollStepMin: 300,     // Minimum pixels to scroll
    scrollStepMax: 800,     // Maximum pixels to scroll
    clickProbability: 0.4   // 40% chance to click a button, 60% chance to scroll
  };

  let actionCount = 0;

  function getRandomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
  }

  function isElementVisible(el) {
    if (!el) return false;
    const style = window.getComputedStyle(el);
    if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
      return false;
    }
    const rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }

  function performScroll() {
    const direction = Math.random() > 0.2 ? 1 : -1; // 80% down, 20% up
    const distance = getRandomInt(CONFIG.scrollStepMin, CONFIG.scrollStepMax) * direction;
    window.scrollBy({ top: distance, behavior: 'smooth' });
    window.__autoBotScrolls = (window.__autoBotScrolls || 0) + 1;
  }

  function performRandomClick() {
    const candidates = Array.from(document.querySelectorAll('button, a, [role="button"], input[type="button"], input[type="submit"]'));
    const validTargets = candidates.filter(el => {
      if (!isElementVisible(el) || el.disabled) return false;
      if (el.tagName.toLowerCase() === 'a') {
        const href = el.getAttribute('href') || '';
        if (href.startsWith('mailto:') || href.startsWith('tel:') || el.hasAttribute('download')) {
          return false;
        }
        if (href.startsWith('http') && !href.startsWith(window.location.origin)) {
          return false;
        }
      }
      return true;
    });

    if (validTargets.length > 0) {
      const target = validTargets[getRandomInt(0, validTargets.length - 1)];
      target.scrollIntoView({ behavior: 'smooth', block: 'center' });
      setTimeout(() => {
        target.click();
        window.__autoBotClicks = (window.__autoBotClicks || 0) + 1;
      }, 500);
    } else {
      performScroll();
    }
  }

  function runLoop() {
    actionCount++;

    if (actionCount > CONFIG.actionsBeforeReload) {
      window.location.reload();
      return;
    }

    if (Math.random() < CONFIG.clickProbability) {
      performRandomClick();
    } else {
      performScroll();
    }

    setTimeout(runLoop, getRandomInt(CONFIG.minDelayMs, CONFIG.maxDelayMs));
  }

  setTimeout(runLoop, 2000);
})();
""".trimIndent()
}
