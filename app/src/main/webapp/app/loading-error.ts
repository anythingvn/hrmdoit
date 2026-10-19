// Hiện khối báo lỗi nếu ứng dụng chưa hiển thị sau 4 giây kể từ khi trang tải xong.
// Tách khỏi index.html (trước là script inline) để CSP chỉ cần script-src 'self'; là entry riêng nên vẫn chạy khi bundle chính lỗi.
const showError = () => {
  const errorElm = document.getElementById('jhipster-error');
  if (errorElm?.style) {
    errorElm.style.display = 'block';
  }
};

window.addEventListener('load', () => {
  setTimeout(showError, 4000);
});

export {};
