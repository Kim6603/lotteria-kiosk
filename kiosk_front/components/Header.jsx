import { useNavigate } from 'react-router-dom';
import '../src/assets/styles/Header.css';

function Header(){
    const navigate = useNavigate();

    const goHome = () => {
        navigate("/");
    }

    return(
        <div id="headerContainer">
            <img src="/images/logo.png" alt="로고" id='logoImg' onClick={() => goHome()}/>
        </div>
    )
}

export default Header;