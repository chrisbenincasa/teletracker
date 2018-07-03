import * as apisauce from 'apisauce';
import { merge } from 'ramda';
import Config from 'react-native-config';

import { User } from '../Model';

export interface TeletrackerApiOptions {
    url?: string,
    token?: string
}

type Required<T> = {
    [K in keyof T]: T[K]
}

const DefaultTeletrackerApiOptions: Required<TeletrackerApiOptions> = {
    url: Config.TELETRACKER_API_URL,
    token: null
}

export class TeletrackerApi {
    private api: apisauce.ApisauceInstance
    private token: string

    constructor(opts?: TeletrackerApiOptions) {
        let options = merge(DefaultTeletrackerApiOptions, opts || {});

        this.api = apisauce.create({
            baseURL: options.url
        });

        this.token = options.token;

        this.api.addRequestTransform(req => {
            if (this.token) {
                Object.assign(req.headers, this.authHeaders());
            }
        })
    }

    setToken(token: string) {
        this.token = token;
    }

    clearToken() {
        this.token = null;
    }

    async getAuthStatus(): Promise<apisauce.ApiResponse<any>> {
        return this.api.get('/api/v1/auth/status');
    }

    async getUser(id: string | number) {
        if (!this.token) {
            return Promise.reject(new Error('getUser requires a token to be set'));
        }

        return this.api.get<User>(`/api/v1/users/${id}`, {}, { headers: this.authHeaders() });
    }

    async getUserSelf() {
        if (!this.token) {
            return Promise.reject(new Error('getUser requires a token to be set'));
        }

        return this.api.get<User>('/api/v1/users/self', {}, { headers: this.authHeaders() });
    }

    async loginUser(email: string, password: string) {
        const data = { email, password };
        return this.api.post<any>('/api/v1/auth/login', data).then(response => {
            if (response.ok) {
                this.setToken(response.data.data.token);
            }

            return response;
        });
    }

    async registerUser(username: string, email: string, password: string) {
        const data = { username, email, password, name: username };
        return this.api.post<any>('/api/v1/users', data).then(response => {
            if (response.ok) {
                this.setToken(response.data.data.token);
            }
            
            return response;
        });
    }

    async search(searchText: string) {
        return this.api.get<any>('/api/v1/search', { query: searchText });
    }

    async addItemToList(listId: string, itemId: string) {
        if (!this.token) {
            return Promise.reject(new Error('getUser requires a token to be set'));
        }

        return this.api.put<any>(`/api/v1/users/self/lists/${listId}/tracked`, { itemId }, { headers: this.authHeaders() });
    }

    async postEvent(eventType: string, targetType: string, targetId: string, details: string) {
        this.withTokenCheck(async () => {
            return this.api.post<any>('/api/v1/users/self/events', { 
                event: {
                    type: eventType,
                    targetEntityType: targetType,
                    targetEntityId: targetId,
                    details
                }
            });
        });
    }

    private withTokenCheck<T>(f: () => Promise<T>): Promise<T> {
        if (!this.token) {
            return Promise.reject(new Error('getUser requires a token to be set'));
        } else {
            return f();
        }
    }

    private authHeaders() {
        return {
            'Authorization': `Bearer ${this.token}`
        };
    }
}
