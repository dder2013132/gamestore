document.addEventListener('DOMContentLoaded', () => {
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
