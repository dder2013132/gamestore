document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('[data-hero-carousel]').forEach(carousel => {
    const slides = [...carousel.querySelectorAll('[data-hero-slide]')];
    const dots = [...carousel.querySelectorAll('[data-hero-dot]')];
    if (slides.length < 2 || dots.length !== slides.length) return;
    const controls = carousel.querySelector('[data-hero-controls]');
    const track = carousel.querySelector('[data-hero-slides]');
    const play = carousel.querySelector('[data-hero-play]');
    const icon = carousel.querySelector('[data-hero-play-icon]');
    const count = carousel.querySelector('[data-hero-count]');
    let index = 0, playing = true, hovering = false, inView = true, timer;

    const schedule = () => {
      clearTimeout(timer);
      if (playing && !hovering && !document.hidden && inView) {
        timer = setTimeout(() => { show(index + 1); }, 6000);
      }
    };
    const renderPlayback = () => {
      play.setAttribute('aria-label', playing ? '自動切替を停止' : '自動切替を再生');
      icon.textContent = playing ? 'Ⅱ' : '▶';
      track.setAttribute('aria-live', playing ? 'off' : 'polite');
      schedule();
    };
    const show = next => {
      index = (next + slides.length) % slides.length;
      slides.forEach((slide, i) => { slide.hidden = i !== index; });
      dots.forEach((dot, i) => { dot.setAttribute('aria-current', String(i === index)); });
      count.textContent = `${String(index + 1).padStart(2, '0')} / ${String(slides.length).padStart(2, '0')}`;
      schedule();
    };
    const select = next => { playing = false; renderPlayback(); show(next); };
    dots.forEach((dot, i) => dot.addEventListener('click', () => select(i)));
    carousel.querySelector('[data-hero-prev]').addEventListener('click', () => select(index - 1));
    carousel.querySelector('[data-hero-next]').addEventListener('click', () => select(index + 1));
    play.addEventListener('click', () => { playing = !playing; renderPlayback(); });
    carousel.addEventListener('pointerenter', event => {
      if (event.pointerType === 'touch') return;
      hovering = true; schedule();
    });
    carousel.addEventListener('pointerleave', () => { hovering = false; schedule(); });
    carousel.addEventListener('focusin', event => {
      // Keep a focused game link in place until the visitor explicitly resumes rotation.
      if (event.target === play) return;
      playing = false; renderPlayback();
    });
    controls.addEventListener('keydown', event => {
      if (event.altKey || event.ctrlKey || event.metaKey || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? slides.length - 1 : index + (event.key === 'ArrowRight' ? 1 : -1);
      select(next);
      if (dots.includes(event.target)) dots[index].focus();
    });
    document.addEventListener('visibilitychange', schedule);
    window.addEventListener('pagehide', () => clearTimeout(timer));
    window.addEventListener('pageshow', () => { playing = true; renderPlayback(); });
    if (window.IntersectionObserver) {
      const observer = new IntersectionObserver(entries => {
        inView = entries[0].isIntersecting;
        schedule();
      }, { threshold: .2 });
      observer.observe(carousel);
    }
    controls.hidden = false;
    carousel.classList.add('is-enhanced');
    show(0); renderPlayback();
  });
});
