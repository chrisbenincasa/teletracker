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
    }

    setToken(token: string) {
        this.token = token;
    }

    getUser(id: string | number) {
        if (!this.token) {
            return Promise.reject(new Error('getUser requires a token to be set'));
        }

        return this.api.get<User>(`/api/v1/users/${id}`, {}, { headers: this.authHeaders() });
    }

    private authHeaders() {
        return {
            'Authorization': `Bearer ${this.token}`
        };
    }
}
