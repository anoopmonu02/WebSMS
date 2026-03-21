/*const sidebarToggle = document.querySelector("#sidebar-toggle");
sidebarToggle.addEventListener("click",function(){
    document.querySelector("#sidebar").classList.toggle("collapsed");
});

document.querySelector(".theme-toggle").addEventListener("click",() => {
    toggleLocalStorage();
    toggleRootClass();
});

function toggleRootClass(){
    const current = document.documentElement.getAttribute('data-bs-theme');
    const inverted = current == 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-bs-theme',inverted);
}

function toggleLocalStorage(){
    if(isLight()){
        localStorage.removeItem("light");
    }else{
        localStorage.setItem("light","set");
    }
}

function isLight(){
    return localStorage.getItem("light");
}

if(isLight()){
    toggleRootClass();
}*/
/*
const hamBurger = document.querySelector(".toggle-btn");

hamBurger.addEventListener("click", function () {
    document.querySelector("#sidebar").classList.toggle("expand");
});*/

// ─── Sidebar Toggle (hamburger) ───────────────────────────────────────────────
/*
const hamBurger = document.querySelector(".toggle-btn");

hamBurger.addEventListener("click", function () {
    const sidebar = document.querySelector("#sidebar");
    sidebar.classList.toggle("expand");

    // Persist sidebar open/closed state
    if (sidebar.classList.contains("expand")) {
        localStorage.setItem("sidebarExpanded", "true");
    } else {
        localStorage.removeItem("sidebarExpanded");
    }
});

// ─── Restore sidebar expand state on page load ────────────────────────────────
if (localStorage.getItem("sidebarExpanded") === "true") {
    document.querySelector("#sidebar").classList.add("expand");
}

// ─── Persist active submenu (dropdown) across page reloads ───────────────────
// When any Bootstrap collapse inside the sidebar is shown, save its id.
// When it's hidden, remove it from storage.
const sidebarDropdowns = document.querySelectorAll("#sidebar .sidebar-dropdown");

sidebarDropdowns.forEach(function (dropdown) {
    // Restore open state before Bootstrap initialises so there's no flicker
    if (localStorage.getItem("activeSubmenu") === dropdown.id) {
        dropdown.classList.add("show");

        // Also update the toggle link so the caret / arrow renders correctly
        const toggleLink = document.querySelector(
            '[data-bs-target="#' + dropdown.id + '"]'
        );
        if (toggleLink) {
            toggleLink.setAttribute("aria-expanded", "true");
            toggleLink.classList.remove("collapsed");
        }
    }

    // Listen for Bootstrap collapse events
    dropdown.addEventListener("show.bs.collapse", function () {
        localStorage.setItem("activeSubmenu", this.id);
    });

    dropdown.addEventListener("hide.bs.collapse", function () {
        // Only remove if this is the one that was stored
        if (localStorage.getItem("activeSubmenu") === this.id) {
            localStorage.removeItem("activeSubmenu");
        }
    });
});*/



/* =============================================================
   script.js  —  Avadh-Shiksha Portal
   Works with the new base.html sidebar (id="sidebar",
   toggle button id="sidebarToggle", collapsed class = "collapsed")
   ============================================================= */

/* =============================================================
   script.js  —  Avadh-Shiksha Portal
   Works with the new base.html sidebar (id="sidebar",
   toggle button id="sidebarToggle", collapsed class = "collapsed")
   ============================================================= */

(function () {
    'use strict';

    /* ----------------------------------------------------------
       1. ELEMENT REFS
    ---------------------------------------------------------- */
    const sidebar    = document.getElementById('sidebar');
    const toggleBtn  = document.getElementById('sidebarToggle');
    const overlay    = document.getElementById('sidebarOverlay');

    if (!sidebar || !toggleBtn) return; // guard — exits if base layout not present

    /* ----------------------------------------------------------
       2. HELPERS
    ---------------------------------------------------------- */
    function isMobile() {
        return window.innerWidth <= 768;
    }

    /* ----------------------------------------------------------
       3. SIDEBAR OPEN / CLOSE
    ---------------------------------------------------------- */
    function setSidebarState(collapsed) {
        if (isMobile()) {
            // Mobile: slide drawer in/out
            sidebar.classList.toggle('mobile-open', !collapsed);
            if (overlay) overlay.classList.toggle('active', !collapsed);
        } else {
            // Desktop: shrink to icon rail
            sidebar.classList.toggle('collapsed', collapsed);
            try {
                localStorage.setItem('sidebarCollapsed', String(collapsed));
            } catch (e) { /* storage blocked */ }
        }
    }

    // Restore desktop state on page load
    if (!isMobile()) {
        try {
            if (localStorage.getItem('sidebarCollapsed') === 'true') {
                sidebar.classList.add('collapsed');
            }
        } catch (e) { /* storage blocked */ }
    }

    // Hamburger click
    toggleBtn.addEventListener('click', function () {
        if (isMobile()) {
            const isOpen = sidebar.classList.contains('mobile-open');
            setSidebarState(isOpen);          // if open → close, vice-versa
        } else {
            const isCollapsed = sidebar.classList.contains('collapsed');
            setSidebarState(!isCollapsed);    // toggle
        }
    });

    // Close on overlay tap (mobile)
    if (overlay) {
        overlay.addEventListener('click', function () {
            setSidebarState(true);
        });
    }

    /* ----------------------------------------------------------
       4. PERSIST ACTIVE SUBMENU ACROSS PAGE RELOADS
    ---------------------------------------------------------- */
    const dropdowns = document.querySelectorAll('#sidebar .sidebar-dropdown');

    dropdowns.forEach(function (dropdown) {
        if (!dropdown.id) return;

        // Restore open state synchronously before Bootstrap boots
        // (prevents collapse flicker)
        try {
            if (localStorage.getItem('activeSubmenu') === dropdown.id) {
                dropdown.classList.add('show');
                const trigger = document.querySelector(
                    '[data-bs-target="#' + dropdown.id + '"]'
                );
                if (trigger) {
                    trigger.setAttribute('aria-expanded', 'true');
                    trigger.classList.remove('collapsed');
                }
            }
        } catch (e) { /* storage blocked */ }

        // Save on open
        dropdown.addEventListener('show.bs.collapse', function () {
            try { localStorage.setItem('activeSubmenu', this.id); } catch (e) {}
        });

        // Remove on close
        dropdown.addEventListener('hide.bs.collapse', function () {
            try {
                if (localStorage.getItem('activeSubmenu') === this.id) {
                    localStorage.removeItem('activeSubmenu');
                }
            } catch (e) {}
        });
    });

    /* ----------------------------------------------------------
       5. MARK ACTIVE LINK based on current URL
    ---------------------------------------------------------- */
    (function markActiveLink() {
        const path = window.location.pathname;
        let bestMatch = null;
        let bestLength = 0;

        document.querySelectorAll('.sidebar-link').forEach(function (link) {
            const href = link.getAttribute('href');
            if (!href || href === '#') return;

            // Strip Thymeleaf context path artifacts if present
            const cleanHref = href.split('?')[0];

            if (path === cleanHref || path.startsWith(cleanHref + '/')) {
                // Longest-match wins (most specific link)
                if (cleanHref.length > bestLength) {
                    bestLength = cleanHref.length;
                    bestMatch = link;
                }
            }
        });

        if (bestMatch) {
            bestMatch.classList.add('active');

            // Expand every ancestor .sidebar-dropdown
            let parent = bestMatch.closest('.sidebar-dropdown');
            while (parent) {
                parent.classList.add('show');
                const trigger = document.querySelector(
                    '[data-bs-target="#' + parent.id + '"]'
                );
                if (trigger) {
                    trigger.setAttribute('aria-expanded', 'true');
                    trigger.classList.remove('collapsed');
                }
                // Walk up another level (nested Reports → sub-group)
                const grandParent = parent.parentElement
                    ? parent.parentElement.closest('.sidebar-dropdown')
                    : null;
                parent = grandParent;
            }
        }
    })();

    /* ----------------------------------------------------------
       6. FEE AMOUNT VISIBILITY TOGGLE
       (Used on dashboard / fee pages)
    ---------------------------------------------------------- */
    document.addEventListener('change', function (e) {
        if (!e.target.classList.contains('toggle-fee-amount')) return;
        const card      = e.target.closest('.card-body');
        if (!card) return;
        const amountEl  = card.querySelector('.fee-amount');
        if (!amountEl) return;
        const actual    = amountEl.dataset.actual || '';
        amountEl.textContent = e.target.checked ? actual : '******';
    });

})();