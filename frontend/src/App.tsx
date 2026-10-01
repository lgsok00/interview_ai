import {BrowserRouter, Navigate, Route, Routes} from 'react-router-dom'
import {AuthProvider} from './auth/AuthProvider'
import {ProtectedRoute} from './auth/ProtectedRoute'
import {HomePage} from './pages/HomePage'
import {OAuthCallbackPage} from './pages/OAuthCallbackPage'
import {LoginPage} from './pages/LoginPage'
import {AdminRoute} from './auth/AdminRoute'
import {AdminCatalogPage} from './pages/AdminCatalogPage'
import {CatalogPage} from './pages/CatalogPage'
import {InterviewSessionPage} from './pages/InterviewSessionPage'
import {InterviewResultPage} from './pages/InterviewResultPage'
import {InterviewGrowthPage} from './pages/InterviewGrowthPage'
import {DocumentsPage} from './pages/DocumentsPage'
import {InterviewSessionListPage} from './pages/InterviewSessionListPage'

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
                        <Route path="/catalog" element={<CatalogPage/>}/>
                        <Route path="/interviews" element={<InterviewSessionListPage/>}/>
                        <Route path="/interviews/:sessionId" element={<InterviewSessionPage/>}/>
                        <Route path="/interviews/:sessionId/result" element={<InterviewResultPage/>}/>
                        <Route path="/growth" element={<InterviewGrowthPage/>}/>
                        <Route path="/documents" element={<DocumentsPage/>}/>
                        <Route path="/admin" element={<AdminRoute><AdminCatalogPage/></AdminRoute>}/>
                    </Route>

                    <Route path="*" element={<Navigate to="/" replace/>}/>
                </Routes>
            </AuthProvider>
        </BrowserRouter>
    )
}

export default App
