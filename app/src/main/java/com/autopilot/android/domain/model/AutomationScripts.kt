package com.autopilot.android.domain.model

object AutomationScripts {
    /**
     * Complete Human-like AutoBot Script with smooth random scrolling,
     * intelligent element discovery/clicking, and randomized timing intervals.
     */
    val AUTOBOT_HUMAN_SCRIPT: String = """
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

  // Helper: Random integer between min and max
  function getRandomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
  }

  // Helper: Check if an element is actually visible on screen
  function isElementVisible(el) {
    if (!el) return false;
    const style = window.getComputedStyle(el);
    if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
      return false;
    }
    const rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }

  // Action 1: Smooth random scrolling (mostly down, occasionally up)
  function performScroll() {
    const direction = Math.random() > 0.2 ? 1 : -1; // 80% down, 20% up
    const distance = getRandomInt(CONFIG.scrollStepMin, CONFIG.scrollStepMax) * direction;
    
    window.scrollBy({
      top: distance,
      behavior: 'smooth'
    });
  }

  // Action 2: Find and click a random safe button or internal link
  function performRandomClick() {
    // Select standard clickable elements
    const candidates = Array.from(document.querySelectorAll('button, a, [role="button"], input[type="button"], input[type="submit"]'));
    
    // Filter for elements that are visible and safe to click
    const validTargets = candidates.filter(el => {
      if (!isElementVisible(el) || el.disabled) return false;
      
      // If it's a link, avoid external domains, downloads, or mailto/tel links
      if (el.tagName.toLowerCase() === 'a') {
        const href = el.getAttribute('href') || '';
        if (href.startsWith('mailto:') || href.startsWith('tel:') || el.hasAttribute('download')) {
          return false;
        }
        // Keep navigation within the same origin if it's a full URL
        if (href.startsWith('http') && !href.startsWith(window.location.origin)) {
          return false;
        }
      }
      return true;
    });

    if (validTargets.length > 0) {
      const target = validTargets[getRandomInt(0, validTargets.length - 1)];
      
      // Scroll the element into view before clicking so it mimics real user behavior
      target.scrollIntoView({ behavior: 'smooth', block: 'center' });
      
      setTimeout(() => {
        target.click();
      }, 500);
    } else {
      // Fallback to scrolling if no clickable elements are found
      performScroll();
    }
  }

  // Main execution loop
  function runLoop() {
    actionCount++;

    // Reload the page once the action limit is reached
    if (actionCount > CONFIG.actionsBeforeReload) {
      window.location.reload();
      return;
    }

    // Decide whether to click or scroll
    if (Math.random() < CONFIG.clickProbability) {
      performRandomClick();
    } else {
      performScroll();
    }

    // Schedule the next action with a randomized delay
    const nextDelay = getRandomInt(CONFIG.minDelayMs, CONFIG.maxDelayMs);
    setTimeout(runLoop, nextDelay);
  }

  // Start the loop after an initial 2-second grace period for page assets to settle
  setTimeout(runLoop, 2000);
})();
""".trimIndent()

    /**
     * Safe wrapper/normalizer to ensure scripts starting with `function()` or missing `(`
     * will execute seamlessly without JavaScript syntax errors.
     */
    fun normalizeScript(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("function(") || trimmed.startsWith("function (")) {
            return if (trimmed.endsWith(");") || trimmed.endsWith(")")) {
                "($trimmed"
            } else {
                "($trimmed)();"
            }
        }
        return raw
    }
}
