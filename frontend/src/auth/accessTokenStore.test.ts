import {afterEach, describe, expect, it} from 'vitest'
import {accessTokenStore} from './accessTokenStore'

describe('accessTokenStore', () => {
    afterEach(() => {
        accessTokenStore.clear()
    })

    it('Access Token을 정규화해 메모리에 저장하고 삭제한다', () => {
        accessTokenStore.set('  access-token  ')

        expect(accessTokenStore.get()).toBe('access-token')

        accessTokenStore.clear()

        expect(accessTokenStore.get()).toBeNull()
    })

    it('빈 Access Token 저장을 거부한다', () => {
        expect(() => accessTokenStore.set('   ')).toThrow(
            'Access Token cannot be empty.',
        )
    })
})
