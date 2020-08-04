import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { DeepReadonly } from '../../types';
import { AppThunk } from '../../app/store';
import { getTasks, SearchTasksRequest, Task } from '../../util/apiClient';

type TasksState = DeepReadonly<{
  tasks: Task[];
  isLoading: boolean;
}>;

const initialState: TasksState = {
  tasks: [],
  isLoading: true,
};

type GetTasksPayload = DeepReadonly<{
  tasks: Task[];
}>;

export const tasksSlice = createSlice({
  name: 'tasks',
  initialState,
  reducers: {
    getTasksInitiated: (state) => {
      state.isLoading = true;
    },
    getTasksSuccess: (state, action: PayloadAction<GetTasksPayload>) => {
      state.isLoading = false;
      state.tasks = action.payload.tasks;
    },
  },
});

export const { getTasksInitiated, getTasksSuccess } = tasksSlice.actions;

export const fetchTasksAsync = (
  request: SearchTasksRequest,
): AppThunk => async (dispatch) => {
  dispatch(getTasksInitiated());

  try {
    const response = await getTasks(request);

    if (response.status)
      dispatch(
        getTasksSuccess({
          tasks: response.data.data,
          // append: request.bookmark !== undefined,
          // bookmark: response.data.paging?.bookmark,
          // total: response.data.paging?.total,
          // state: request.matchState,
        }),
      );
  } catch (e) {
    console.error(e);
  }
};

export default tasksSlice.reducer;
