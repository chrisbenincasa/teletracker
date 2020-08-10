import { configureStore, ThunkAction, Action } from '@reduxjs/toolkit';
import counterReducer from '../features/counter/counterSlice';
import matchingReducer from '../features/matching/matchingSlice';
import tasksReducer from '../features/tasks/tasksSlice';

export const store = configureStore({
  reducer: {
    counter: counterReducer,
    matching: matchingReducer,
    tasks: tasksReducer,
  },
});

declare module 'react-redux' {
  export interface DefaultRootState extends RootState {}
}

export type RootState = ReturnType<typeof store.getState>;
export type AppThunk<ReturnType = void> = ThunkAction<
  ReturnType,
  RootState,
  unknown,
  Action<string>
>;
