(() => {
  const choiceKey = 'sdmse.language';
  const dismissedKey = 'sdmse.languageSuggestionDismissed';
  const read = (storage, key) => {
    try { return window[storage].getItem(key); } catch (_) { return null; }
  };
  const write = (storage, key, value) => {
    try { window[storage].setItem(key, value); } catch (_) { /* Storage is optional. */ }
  };

  // Switching language keeps the reader at the same section
  const banner = document.querySelector('.language-suggestion');
  const switches = [...document.querySelectorAll('a[data-language]')];
  switches.forEach(link => {
    link.dataset.base = link.getAttribute('href');
    if (!link.closest('.language-menu')) return;
    link.addEventListener('click', () => {
      write('localStorage', choiceKey, link.dataset.language);
      if (link.getAttribute('aria-current') === 'page') {
        if (banner) banner.hidden = true;
        link.closest('details').open = false;
      }
    });
  });
  const menu = document.querySelector('.language-menu');
  if (menu) {
    document.addEventListener('click', event => {
      if (!menu.contains(event.target)) menu.open = false;
    });
    menu.addEventListener('keydown', event => {
      if (event.key !== 'Escape' || !menu.open) return;
      menu.open = false;
      menu.querySelector('summary').focus();
    });
  }

  const syncHash = () => switches.forEach(link => { link.href = link.dataset.base + location.hash; });
  syncHash();
  window.addEventListener('hashchange', syncHash);

  if (!banner || read('sessionStorage', dismissedKey)) return;

  const versions = new Map(switches.filter(link => link.closest('.language-menu')).map(link => [link.dataset.language, link]));

  // e.g. de-AT -> de, pt-PT -> pt-BR, zh-CN -> zh-Hans, zh-TW -> none
  const match = value => {
    const parts = value.toLowerCase().split('-');
    if (parts[0] === 'zh') {
      if (parts.includes('hant') || (!parts.includes('hans') && parts.some(part => ['tw', 'hk', 'mo'].includes(part)))) return null;
      return versions.has('zh-Hans') ? 'zh-Hans' : null;
    }
    const code = parts[0] === 'pt' ? 'pt-BR' : parts[0];
    return versions.has(code) ? code : null;
  };

  const choice = read('localStorage', choiceKey);
  const code = versions.has(choice) ? choice : (navigator.languages || [navigator.language]).map(match).find(Boolean);
  if (!code || code === 'en') return;

  const target = versions.get(code);
  const link = document.createElement('a');
  link.dataset.language = code;
  link.dataset.base = target.dataset.base;
  link.lang = code;
  link.textContent = target.dataset.suggest;
  link.addEventListener('click', () => write('localStorage', choiceKey, code));
  switches.push(link);
  syncHash();
  const dismiss = document.createElement('button');
  dismiss.type = 'button';
  dismiss.lang = code;
  dismiss.textContent = target.dataset.dismiss;
  dismiss.addEventListener('click', () => {
    write('sessionStorage', dismissedKey, '1');
    banner.hidden = true;
  });
  banner.dir = target.dir;
  banner.replaceChildren(link, ' ', dismiss);
  banner.hidden = false;
})();
