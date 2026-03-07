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
});