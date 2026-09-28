import {BrowserRouter, Navigate, Route, Routes} from 'react-router-dom'
import {AuthProvider} from './auth/AuthProvider'
import {ProtectedRoute} from './auth/ProtectedRoute'
import {HomePage} from './pages/HomePage'
import {OAuthCallbackPage} from './pages/OAuthCallbackPage'
import {LoginPage} from './pages/LoginPage'

import './App.css'

function App() {
    return (
        <BrowserRouter>
            <AuthProvider>
                <Routes>
                    <Route path="/login" element={<LoginPage/>}/>
                    <Route path="/oauth/callback" element={<OAuthCallbackPage/>}/>

                    <Route element={<ProtectedRoute/>}>
                        <Route path="/" element={<HomePage/>}/>
                    </Route>

                    <Route path="*" element={<Navigate to="/" replace/>}/>
                </Routes>
            </AuthProvider>
        </BrowserRouter>
    )
}

export default App