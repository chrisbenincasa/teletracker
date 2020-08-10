import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { DeepReadonly } from '../../types';
import { AppThunk } from '../../app/store';
import {
  getTasks,
  SearchTasksRequest,
  Task,
  GetTaskRequest,
  getTask,
} from '../../util/apiClient';

type TasksState = DeepReadonly<{
  tasks: Task[];
  isLoading: boolean;
  currentTaskFilter?: string;
  isLoadingTaskDetail: boolean;
  taskDetail?: Task;
}>;

const initialState: TasksState = {
  tasks: [],
  isLoading: true,
  isLoadingTaskDetail: false,
};

type GetTasksPayload = DeepReadonly<{
  tasks: Task[];
  currentTaskFilter?: string;
}>;

type GetTaskPayload = DeepReadonly<{
  task: Task;
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
      state.currentTaskFilter = action.payload.currentTaskFilter;
    },
    getTaskInitiated: (state) => {
      state.isLoadingTaskDetail = true;
    },
    getTaskSuccess: (state, action: PayloadAction<GetTaskPayload>) => {
      state.isLoadingTaskDetail = false;
      state.taskDetail = action.payload.task;
    },
  },
});

export const {
  getTasksInitiated,
  getTasksSuccess,
  getTaskInitiated,
  getTaskSuccess,
} = tasksSlice.actions;

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
          currentTaskFilter: request.taskName,
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

export const fetchTaskAsync = (request: GetTaskRequest): AppThunk => async (
  dispatch,
) => {
  dispatch(getTaskInitiated());
  try {
    const response = await getTask(request);
    if (response.status) {
      dispatch(getTaskSuccess({ task: response.data.data }));
    }
  } catch (e) {
    console.error(e);
  }
};

export default tasksSlice.reducer;
