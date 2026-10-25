document.addEventListener('DOMContentLoaded', () => {
  if (!window.fetch || !window.FormData || !window.AbortController || !window.DOMParser) return;
  const notice = document.querySelector('[data-wishlist-notice]');
  if (!notice) return;
  const message = notice.querySelector('[data-wishlist-message]');
  const recovery = notice.querySelector('[data-wishlist-recovery]');
  const pending = new Map();
  let lastInteraction = null;
  let noticeTimeout;
  const forms = () => [...document.querySelectorAll('[data-wishlist-form]')];
  const listRoot = () => document.querySelector('[data-wishlist-content]');

  const showNotice = (text, error = false, href = '', label = '') => {
    clearTimeout(noticeTimeout);
    notice.classList.toggle('is-error', error);
    notice.hidden = false;
    message.textContent = text;
    recovery.hidden = !href;
    recovery.textContent = label;
    if (href) recovery.href = href;
    else recovery.removeAttribute('href');
    if (!error) noticeTimeout = setTimeout(() => { notice.hidden = true; }, 4000);
  };
  notice.querySelector('[data-wishlist-dismiss]').addEventListener('click', () => { clearTimeout(noticeTimeout); notice.hidden = true; });

  const paint = (id, saved) => {
    forms().filter(form => form.dataset.gameId === id).forEach(form => {
      form.dataset.saved = String(saved);
      form.action = saved ? form.dataset.removeUrl : form.dataset.addUrl;
      const button = form.querySelector('.wishlist-button');
      button.classList.toggle('is-saved', saved);
      button.setAttribute('aria-pressed', String(saved));
      button.setAttribute('aria-label', `${form.dataset.title} ${saved ? 'をお気に入りから削除' : 'をお気に入りに追加'}`);
      button.querySelector('svg').setAttribute('fill', saved ? 'currentColor' : 'none');
    });
    document.querySelectorAll('[data-wishlist-label]').forEach(label => {
      if (label.dataset.gameId === id) label.textContent = saved ? 'お気に入りに保存済み' : 'お気に入りに保存';
    });
  };
  const updateBusy = () => {
    const wholeListBusy = !!listRoot() && pending.size > 0;
    forms().forEach(form => {
      const busy = wholeListBusy || pending.has(form.dataset.gameId);
      form.querySelector('.wishlist-button').disabled = busy;
      form.setAttribute('aria-busy', String(busy));
    });
  };
  const refreshList = async (signal, csrf) => {
    const root = listRoot();
    const response = await fetch(window.location.href, { credentials: 'same-origin', redirect: 'error', headers: { Accept: 'text/html' }, signal });
    if (!response.ok) throw new Error('List refresh failed');
    const page = new DOMParser().parseFromString(await response.text(), 'text/html');
    const next = page.querySelector('[data-wishlist-content]');
    if (!next || next.dataset.wishlistSession !== csrf) throw new Error('Wishlist session changed');
    const replacement = document.importNode(next, true);
    root.replaceWith(replacement);
    const canonicalPage = Number(replacement.dataset.page);
    if (Number.isSafeInteger(canonicalPage) && canonicalPage >= 1) {
      const url = new URL(window.location.href);
      if (canonicalPage > 1) url.searchParams.set('page', String(canonicalPage));
      else url.searchParams.delete('page');
      if (url.href !== window.location.href) window.history.replaceState(null, '', url);
    }
    updateBusy();
    return replacement.querySelector('.wishlist-button') || replacement.querySelector('a');
  };

  document.addEventListener('submit', async event => {
    const form = event.target;
    if (!form.matches('[data-wishlist-form]')) return;
    event.preventDefault();
    const id = form.dataset.gameId;
    if (pending.has(id) || (listRoot() && pending.size > 0)) return;
    const old = form.dataset.saved === 'true';
    const target = !old;
    const action = target ? form.dataset.addUrl : form.dataset.removeUrl;
    const body = new URLSearchParams(new FormData(form));
    const csrf = body.get('csrfToken');
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    const interaction = {};
    lastInteraction = interaction;
    const hadFocus = form.contains(document.activeElement);
    let focusTarget = form.querySelector('.wishlist-button');
    let confirmed = false;
    pending.set(id, interaction);
    paint(id, target);
    updateBusy();
    try {
      const response = await fetch(action, {
        method: 'POST', body, credentials: 'same-origin', redirect: 'error',
        headers: { Accept: 'application/json' }, signal: controller.signal,
      });
      const contentType = response.headers.get('content-type') || '';
      if (!contentType.toLowerCase().includes('application/json')) throw new Error('Unexpected wishlist response');
      const result = await response.json();
      if (!response.ok) {
        const error = new Error('Wishlist request failed');
        error.code = result.error;
        throw error;
      }
      if (String(result.gameId) !== id || typeof result.saved !== 'boolean') throw new Error('Invalid wishlist state');
      confirmed = true;
      paint(id, result.saved);
      if (listRoot()) {
        try { focusTarget = await refreshList(controller.signal, csrf); }
        catch {
          showNotice('お気に入りは更新しましたが、一覧を読み込めませんでした。', true, window.location.href, '一覧を読み直す');
          return;
        }
      }
      showNotice(result.saved ? 'お気に入りに追加しました。' : 'お気に入りから外しました。');
    } catch (error) {
      if (!confirmed) paint(id, old);
      if (error.code === 'login_required') showNotice('ログインの有効期限が切れました。ログインし直してください。', true, form.dataset.loginUrl, 'ログインする');
      else if (error.code === 'csrf_invalid') showNotice('ページの有効期限が切れました。読み直してお試しください。', true, window.location.href, 'ページを読み直す');
      else if (error.code === 'game_not_found') showNotice('このゲームは見つかりませんでした。', true);
      else showNotice('保存結果を確認できませんでした。もう一度押すか、お気に入り一覧で確認してください。', true);
    } finally {
      clearTimeout(timeout);
      pending.delete(id);
      updateBusy();
      if (lastInteraction === interaction && hadFocus && document.activeElement === document.body && focusTarget?.isConnected && !focusTarget.disabled) {
        focusTarget.focus({ preventScroll: true });
      }
    }
  });
});
