declare module 'reduxsauce' {
    import { AnyAction, ReducersMapObject } from 'redux';

    export interface Actions {
        [action: string]: string[] | null;
    }

    export interface ActionTypes {
        [action: string]: string;
    }

    export interface ActionCreators {
        [action: string]: (...args: any[]) => AnyAction;
    }

    export interface Handlers {
        [action: string]: (state: object, action: object) => object;
    }

    /**
     * Custom options for created types and actions
     *
     * prefix - prepend the string to all created types
     */
    interface Options {
        prefix: string;
    }

    interface CreatedActions {
        Types: ActionTypes;
        Creators: ActionCreators;
    }

    export function createReducer(initialState: object, handlers: Handlers): ReducersMapObject;
    export function createTypes(types: string, options?: Options): ActionTypes;
    export function createActions(actions: Actions, options?: Options): CreatedActions;
}