import { useEffect, useState } from "react";
import "../src/assets/styles/Shop.css";

function Shop(){
  // 상태를 'cart'로 통일해서 화면이 외부 업데이트(cart-updated 이벤트)에 즉시 반응
  const [cart, setCart] = useState([]);

  const recalc = () => {
    try {
      const saved = JSON.parse(sessionStorage.getItem("cart")) || [];
      setCart(Array.isArray(saved) ? saved : []);
    } catch {
      setCart([]);
    }
  };

  useEffect(() => {
    recalc();
    // Bottom.jsx에서 cartOps 반영 시 브로드캐스트하는 이벤트 수신
    const handler = () => recalc();
    window.addEventListener("cart-updated", handler);
    return () => window.removeEventListener("cart-updated", handler);
  }, []);

  const totalPrice = cart.reduce((sum, menu) => sum + (Number(menu.price || 0) * Number(menu.count || 0)), 0);

  const save = (next) => {
    sessionStorage.setItem("cart", JSON.stringify(next));
    setCart(next);
    // 내부 조작 시에도 다른 컴포넌트가 알 수 있게 이벤트 발행
    window.dispatchEvent(new CustomEvent("cart-updated", { detail: { cart: next } }));
  };

  const removeItem = (index) => {
    const next = cart.filter((_, i) => i !== index);
    save(next);
  };

  const increaseCount = (index) => {
    const next = cart.map((it, i) => i === index ? { ...it, count: Number(it.count || 0) + 1 } : it);
    save(next);
  };

  const decreaseCount = (index) => {
    const next = cart.map((it, i) => i === index ? { ...it, count: Math.max(1, Number(it.count || 0) - 1) } : it);
    save(next);
  };

  return(
    <div id="shop">
      <div id="shopHeader">
        <span className="shopFont">총주문내역</span>
        <div id="shopCount"><span className="countNum">{cart.length}</span>&nbsp;&nbsp;개</div>
        <span className="shopPrice countNum">{totalPrice.toLocaleString()}원</span>
      </div>
      <div id="shopBody">
        {cart.map((menu, i) => (
          <div className="cartCard" key={`${menu.name}-${i}`}>
            <span className="shopFont">{menu.name}</span>
            <div className="cartCount">
              <div className="cartCountBt" onClick={() => decreaseCount(i)}>-</div>
              <span className="cartCountNum">{menu.count}</span>
              <div className="cartCountBt" onClick={() => increaseCount(i)}>+</div>
            </div>
            <div id="nanoPriceBox">
              <div>
                {(Number(menu.count || 0) * Number(menu.price || 0)).toLocaleString()}원{" "}
                <span className="cartItemDelete" onClick={() => removeItem(i)}>❌</span>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export default Shop;
