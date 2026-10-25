document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('[data-screenshot-picker]').forEach(picker => {
    const preview = picker.closest('.screenshot-slot').querySelector('[data-screenshot-preview]');
    const update = () => {
      const path = picker.value;
      const valid = /^images\/screenshots\/[a-z0-9][a-z0-9_.-]*\.(png|jpe?g|webp)$/.test(path);
      preview.hidden = !valid;
      if (valid) preview.src = picker.dataset.contextPath + '/' + path;
      else preview.removeAttribute('src');
    };
    picker.addEventListener('change', update); update();
  });
  document.querySelectorAll('[data-game-gallery]').forEach(gallery => {
    const main = gallery.querySelector('[data-gallery-main]');
    const thumbs = [...gallery.querySelectorAll('[data-gallery-thumb]')];
    const counter = gallery.querySelector('[data-gallery-counter]');
    const open = gallery.querySelector('[data-gallery-open]');
    const error = gallery.querySelector('[data-gallery-error]');
    const select = index => {
      const thumb = thumbs[index];
      thumbs.forEach((item, i) => item.setAttribute('aria-current', String(i === index)));
      main.hidden = false; error.hidden = true;
      main.alt = thumb.querySelector('img').alt; main.src = thumb.href; open.href = thumb.href;
      counter.textContent = `${index + 1} / ${thumbs.length}`;
    };
    main.addEventListener('error', () => { main.hidden = true; error.hidden = false; });
    main.addEventListener('load', () => { main.hidden = false; error.hidden = true; });
    if (main.complete && main.naturalWidth === 0) { main.hidden = true; error.hidden = false; }
    thumbs.forEach((thumb, index) => {
      thumb.addEventListener('click', event => {
        if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault(); select(index);
      });
      thumb.addEventListener('keydown', event => {
        const next = event.key === 'ArrowRight' ? (index + 1) % thumbs.length
          : event.key === 'ArrowLeft' ? (index - 1 + thumbs.length) % thumbs.length
          : event.key === 'Home' ? 0 : event.key === 'End' ? thumbs.length - 1 : null;
        if (next !== null) { event.preventDefault(); select(next); thumbs[next].focus(); }
      });
    });
  });
  const reviewForms = document.querySelectorAll('[data-review-form]');
  reviewForms.forEach(form => form.addEventListener('submit', event => {
    if (form.dataset.submitting === 'true') { event.preventDefault(); return; }
    form.dataset.submitting = 'true'; form.querySelector('button[type="submit"]').disabled = true;
  }));
  window.addEventListener('pageshow', () => reviewForms.forEach(form => {
    form.dataset.submitting = ''; form.querySelector('button[type="submit"]').disabled = false;
  }));
  const orderForms = document.querySelectorAll('[data-order-action-form]');
  orderForms.forEach(form => form.addEventListener('submit', event => {
    if (form.dataset.submitting === 'true') { event.preventDefault(); return; }
    orderForms.forEach(other => {
      other.dataset.submitting = 'true';
      other.querySelector('button[type="submit"]').disabled = true;
    });
  }));
  window.addEventListener('pageshow', () => orderForms.forEach(form => {
    form.dataset.submitting = '';
    form.querySelector('button[type="submit"]').disabled = false;
  }));
  const picker = document.querySelector('[data-cover-picker]');
  const preview = document.querySelector('[data-cover-preview]');
  if (picker && preview) {
    const update = () => {
      const path = picker.value;
      const valid = /^images\/[a-zA-Z0-9_.-]+\.(png|jpe?g|webp|gif)$/.test(path);
      preview.hidden = !valid;
      if (valid) preview.src = picker.dataset.contextPath + '/' + path;
      else preview.removeAttribute('src');
    };
    picker.addEventListener('change', update);
    update();
  }
  const checkout = document.querySelector('[data-checkout-form]');
  const confirm = checkout?.querySelector('[data-checkout-button]');
  if (checkout && confirm && checkout.dataset.checkoutBlocked !== 'true') {
    const idleText = confirm.textContent;
    checkout.addEventListener('submit', event => {
      if (checkout.dataset.submitting === 'true') { event.preventDefault(); return; }
      checkout.dataset.submitting = 'true';
      confirm.disabled = true;
      confirm.textContent = '注文を処理しています…';
      checkout.setAttribute('aria-busy', 'true');
    });
    window.addEventListener('pageshow', () => {
      checkout.dataset.submitting = '';
      confirm.disabled = false;
      confirm.textContent = idleText;
      checkout.removeAttribute('aria-busy');
    });
  }
});
