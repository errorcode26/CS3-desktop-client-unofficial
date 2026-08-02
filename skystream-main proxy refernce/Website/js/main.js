  function switchTab(btn, id) {
    document.querySelectorAll('.tab-btn').forEach(b => { b.classList.remove('active'); b.setAttribute('aria-selected', 'false'); });
    document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
    btn.classList.add('active');
    btn.setAttribute('aria-selected', 'true');
    document.getElementById('tab-' + id).classList.add('active');
  }

  /* ── FAQ accordion ── */
  function toggleFaq(item) {
    const wasOpen = item.classList.contains('open');
    document.querySelectorAll('.faq-item').forEach(i => { i.classList.remove('open'); i.querySelector('.faq-q').setAttribute('aria-expanded', 'false'); });
    if (!wasOpen) { item.classList.add('open'); item.querySelector('.faq-q').setAttribute('aria-expanded', 'true'); }
  }

  /* ── Scroll reveal ── */
  const io = new IntersectionObserver(entries => {
    entries.forEach(e => {
      if (e.isIntersecting) {
        const delay = e.target.dataset.delay || 0;
        setTimeout(() => e.target.classList.add('visible'), delay);
        io.unobserve(e.target);
      }
    });
  }, { threshold: 0.08, rootMargin: '0px 0px -40px 0px' });

  document.querySelectorAll('.reveal').forEach(el => io.observe(el));

  /* Stagger children inside reveal elements */
  document.querySelectorAll('.feat-row, .contrib-item, .stat').forEach((el, i) => {
    el.style.transitionDelay = (i % 6) * 0.06 + 's';
  });

  /* ── Hamburger / mobile drawer ── */
  const hamburgerBtn = document.getElementById('hamburger-btn');
  const mobDrawer    = document.getElementById('mob-drawer');
  const mobBackdrop  = document.getElementById('mob-backdrop');

  function openDrawer() {
    mobDrawer.classList.add('open');
    hamburgerBtn.classList.add('open');
    hamburgerBtn.setAttribute('aria-expanded', 'true');
    document.body.style.overflow = 'hidden';
  }

  function closeDrawer() {
    mobDrawer.classList.remove('open');
    hamburgerBtn.classList.remove('open');
    hamburgerBtn.setAttribute('aria-expanded', 'false');
    document.body.style.overflow = '';
  }

  hamburgerBtn.addEventListener('click', () => {
    mobDrawer.classList.contains('open') ? closeDrawer() : openDrawer();
  });
  mobBackdrop.addEventListener('click', closeDrawer);

  /* Close on Escape key */
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closeDrawer(); });

  /* ── GitHub live star + download count ── */
  (async () => {
    try {
      const r = await fetch('https://api.github.com/repos/akashdh11/skystream', {
        headers: { Accept: 'application/vnd.github+json' }
      });
      if (!r.ok) throw new Error();
      const d = await r.json();
      const count = d.stargazers_count;
      const label = count >= 1000
        ? (count / 1000).toFixed(count >= 10000 ? 0 : 1) + 'k stars'
        : count + ' stars';
      document.getElementById('gh-star-count').textContent = label;
      document.getElementById('gh-stars-link').setAttribute('aria-label', `GitHub repository, ${label}`);
      const mob = document.getElementById('mob-star-count');
      if (mob) mob.textContent = label;
    } catch {
      document.getElementById('gh-star-count').textContent = 'GitHub';
    }
  })();

  /* Fetch total download count from releases */
  (async () => {
    try {
      const r = await fetch('https://api.github.com/repos/akashdh11/skystream/releases', {
        headers: { Accept: 'application/vnd.github+json' }
      });
      if (!r.ok) throw new Error();
      const releases = await r.json();
      let total = 0;
      releases.forEach(rel => rel.assets.forEach(a => total += a.download_count));
      const el = document.getElementById('gh-dl-count');
      if (el && total > 0) {
        el.textContent = total >= 1000
          ? (total / 1000).toFixed(total >= 10000 ? 0 : 1) + 'k+'
          : total + '+';
      }
    } catch { /* keep fallback */ }
  })();

  /* ── Nav active state (desktop + mobile drawer) ── */
  const navLinks     = document.querySelectorAll('.nav-link[data-nav]');
  const mobNavLinks  = document.querySelectorAll('.mob-nav-link[data-mob-nav]');
  const sections     = ['features', 'install', 'community'];

  function updateNav() {
    let current = '';
    sections.forEach(id => {
      const el = document.getElementById(id);
      if (!el) return;
      if (el.getBoundingClientRect().top <= window.innerHeight * 0.35) current = id;
    });

    /* Desktop pill */
    navLinks.forEach(link => {
      const isActive = link.dataset.nav === current ||
                       (current === '' && link.dataset.nav === 'home');
      link.classList.toggle('active', isActive);
    });

    /* Mobile drawer links */
    mobNavLinks.forEach(link => {
      const isActive = link.dataset.mobNav === current ||
                       (current === '' && link.dataset.mobNav === 'home');
      link.classList.toggle('active', isActive);
    });
  }

  window.addEventListener('scroll', updateNav, { passive: true });
  updateNav();
