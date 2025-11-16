import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

function MenuBody(){
    const location = useLocation();
    const { menu } = location.state || {};
    const navigate = useNavigate();

    const [count, setCount] = useState(1);

    const increase = () => {
        setCount(count+1);
    };

    const decrease = () => {
        if (count > 1) setCount(count-1);
    };

    const addCart = () => {
        let savedCart = JSON.parse(sessionStorage.getItem("cart")) || [];
        const exist = savedCart.find(item => item.name === menu.이름);
        if(exist){
            exist.count += count;
        } else {
            savedCart.push({name: menu.이름, price: menu.가격, count: count});
        }
        sessionStorage.setItem("cart", JSON.stringify(savedCart));
        navigate("/");
    }

    const goHome = () => {
        navigate("/");
    }

    return(
        <>
            <div id="menuBody">
                <div id="backDiv">
                    <img src="/images/back.png" id="backImg" alt="eeeee" onClick={() => goHome()}/>
                </div>
                <img src={`/images/menu/${menu.이름}.png`} alt={menu.이름} id="menuImage"/>
                <div id="menuDetailBox">
                    <span id="menuDetailName">{menu.이름}</span>
                    <span id="menuDetailPrice">{menu.가격.toLocaleString()}원</span>
                    <span id="menuDetail">{menu.설명}</span>
                </div>
                <div id="menuInfoBox">
                    <div className="blank"/>
                    <p className="menuInfo">영양소 정보</p>
                    <div className="menuInfoRow">
                        <span className="infoLeft">총중량g</span>
                        <span className="infoRight">{menu.총중량}</span>
                    </div>
                    <div className="menuInfoRow">
                        <span className="infoLeft">열량kcal</span>
                        <span className="infoRight">{menu.열량}</span>
                    </div>
                    <div className="menuInfoRow">
                        <span className="infoLeft">단백질g(%)</span>
                        <span className="infoRight">{menu.단백질}</span>
                    </div>
                    <div className="menuInfoRow">
                        <span className="infoLeft">나트륨mg(%)</span>
                        <span className="infoRight">{menu.나트륨}</span>
                    </div>
                    <div className="menuInfoRow">
                        <span className="infoLeft">당류g</span>
                        <span className="infoRight">{menu.당류}</span>
                    </div>
                    <div className="menuInfoRow">
                        <span className="infoLeft">포화지방g</span>
                        <span className="infoRight">{menu.포화지방}</span>
                    </div>
                    <div className="blankSamll"/>
                    <ul id="menuNote">
                        <li>고카페인 음료의 경우 어린이, 임산부, 카페인 민감자는 섭취에 주의해 주시기 바랍니다.</li>
                        <li>1회 제공량 기준</li>
                    </ul>
                    <div className="blank"/>
                    <p className="menuInfo">알러지 정보</p>
                    <span className="allergy">{menu.알러지정보}</span><br/>
                    <span className="allergy">※ 구성품 및 옵션의 영양성분은 아래의 링크에서 확인 부탁드립니다.</span><br/>
                    <a href="https://www.lotteeatz.com/upload/etc/ria/items.html" id="allergyBt" target="_blank">영양성분 정보 &gt;</a>
                    <div className="blank"/>
                </div>
            </div>
            <div id="menuBottom">
                <div id="countBox">
                    <button className="counterBtn sizeUp" onClick={decrease}>-</button>
                    <span className="counterValue">{count}</span>
                    <button className="counterBtn" onClick={increase}>+</button>
                </div>
                <div id="priceBt" onClick={() => addCart()}>{(count * menu.가격).toLocaleString()}원 담기</div>
            </div>
        </>
    )
}

export default MenuBody;