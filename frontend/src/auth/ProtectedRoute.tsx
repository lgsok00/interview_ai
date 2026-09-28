import {useAuth} from "./useAuth";
import {Navigate, Outlet, useLocation} from "react-router-dom";

export function ProtectedRoute() {
    const {isAuthenticated, isInitializing} = useAuth()
    const location = useLocation()

    if (isInitializing) {
        return <p role="status">로그인 상태를 확인하고 있습니다.</p>
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" replace state={{from: location}}/>
    }

    return <Outlet/>
}