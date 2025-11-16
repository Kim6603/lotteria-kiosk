import { useEffect } from 'react'
import './App.css'
import { Routes, Route } from 'react-router-dom'
import Home from '../pages/home'
import Menu from '../pages/Menu'
import AiPage from '../pages/AiPage'

function App() {
  useEffect(() => {
    document.title = "롯데리아 키오스크";
  }, []);

  return (
    <>
      <Routes>
        <Route path='/' element={<Home />} />
        <Route path='/menu/:id' element={<Menu/>}/>
        <Route path='/ai' element={<AiPage />}/>
      </Routes>
    </>
  )
}

export default App
