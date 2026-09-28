import {useEffect, useState, type ReactNode} from 'react'
import {Link} from 'react-router-dom'
import {apiRequest} from '../api/client'

interface CurrentUserResponse {
  role: 'USER' | 'ADMIN'
}

interface AdminRouteProps {
  children: ReactNode
}

export function AdminRoute({children}: AdminRouteProps) {
  const [access, setAccess] = useState<'checking' | 'allowed' | 'denied' | 'error'>('checking')

  useEffect(() => {
    let active = true

    void apiRequest<CurrentUserResponse>('/api/users/me', {authenticated: true})
      .then((user) => {
        if (active) {
          setAccess(user.role === 'ADMIN' ? 'allowed' : 'denied')
        }
      })
      .catch(() => {
        if (active) {
          setAccess('error')
        }
      })

    return () => {
      active = false
    }
  }, [])

  if (access === 'checking') {
    return <main className="admin-state"><p role="status">관리자 권한을 확인하고 있습니다.</p></main>
  }

  if (access === 'denied') {
    return (
      <main className="admin-state">
        <h1>관리자 권한이 필요합니다.</h1>
        <p>관리자 계정으로 로그인한 뒤 다시 시도해 주세요.</p>
        <Link className="secondary-button" to="/">홈으로</Link>
      </main>
    )
  }

  if (access === 'error') {
    return (
      <main className="admin-state">
        <h1>권한을 확인하지 못했습니다.</h1>
        <p>네트워크 연결을 확인한 뒤 다시 시도해 주세요.</p>
        <Link className="secondary-button" to="/">홈으로</Link>
      </main>
    )
  }

  return children
}
