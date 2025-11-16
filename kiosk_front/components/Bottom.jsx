import { useEffect, useMemo, useState } from "react";
import { useAiKiosk } from "../hooks/useAiKiosk";
import "../src/assets/styles/Bottom.css";

function Bottom({ onAiRecommend }) {
  const { isRecording, toggleRecording, hardStopAll } = useAiKiosk({ onAiRecommend });

  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [orderNumber, setOrderNumber] = useState(null);

  const showTemporaryModal = (message) => {
    const temp = document.createElement("div");
    temp.className = "tempModal";
    temp.textContent = message;
    document.body.appendChild(temp);
    setTimeout(() => temp.remove(), 1800);
  };

  const cartDeleteAll = () => {
    sessionStorage.removeItem("cart");
    window.dispatchEvent(new CustomEvent("cart-updated", { detail: { cart: [] } }));
    window.location.reload();
  };

  const finalizePayment = () => {
    hardStopAll();
    sessionStorage.removeItem("cart");
    window.dispatchEvent(new CustomEvent("cart-updated", { detail: { cart: [] } }));
    window.location.reload();
  };

  const closePaymentModal = () => {
    setShowPaymentModal(false);
    finalizePayment();
  };

  const scheduleFinalize = (ms = 1800) => {
    setTimeout(() => finalizePayment(), ms);
  };

  const savedCartLen = useMemo(() => {
    try { return (JSON.parse(sessionStorage.getItem("cart") || "[]") || []).length; }
    catch { return 0; }
  }, [showPaymentModal]);

  const cartPay = () => {
    if (!savedCartLen) {
      showTemporaryModal("상품이 없습니다.");
      return;
    }
    const randomOrder = Math.floor(100000 + Math.random() * 900000);
    setOrderNumber(randomOrder);
    setShowPaymentModal(true);
    scheduleFinalize(1800);
  };

  const recordingStyle = isRecording ? { border: "3px solid #FFD400", transform: "scale(0.99)" } : {};

  return (
    <div id="bottom">
      <div id="bottomContainer">
        <div className="bottomBt leftBt" onClick={cartDeleteAll}>취소하기</div>

        <div
          className="bottomBt centerBt"
          onClick={toggleRecording}
          style={recordingStyle}
          aria-pressed={isRecording}
        >
          AI 상담
        </div>

        <div className="bottomBt rightBt" onClick={cartPay}>결제하기</div>
      </div>

      {showPaymentModal && (
        <div id="paymentModalOverlay">
          <div id="paymentModalBox">
            <h2>✅ 주문 완료</h2>
            <p className="orderNumber">주문 번호: {orderNumber}</p>
            <p className="note">잠시 후 메인 화면으로 돌아갑니다.</p>
            <button id="paymentModalConfirm" onClick={closePaymentModal}>확인</button>
          </div>
        </div>
      )}
    </div>
  );
}

export default Bottom;
