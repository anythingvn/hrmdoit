// Hiện khối báo lỗi nếu ứng dụng chưa hiển thị sau 4 giây kể từ khi trang tải xong.
// File tĩnh riêng (không qua bundler, chép nguyên vào gốc build bởi vite-plugin-static-copy), nạp bằng <script src>:
// vẫn chạy khi bundle chính không tải được hoặc lỗi lúc khởi tạo, và không cần script inline (CSP script-src 'self').
/* global window, document */
(function () {
  function showError() {
    var errorElm = document.getElementById('jhipster-error');
    if (errorElm && errorElm.style) {
      errorElm.style.display = 'block';
    }
  }
  window.addEventListener('load', function () {
    setTimeout(showError, 4000);
  });
})();
