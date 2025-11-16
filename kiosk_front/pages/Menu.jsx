import Header from "../components/Header";
import MenuBody from "../components/MenuBody";
import "../src/assets/styles/Menu.css"

function Menu(){
    return(
        <>
            <div id="homeBody">
                <div id="homeRoot">
                    <Header />
                    <MenuBody />
                </div>
            </div>
        </>
    )
}

export default Menu;