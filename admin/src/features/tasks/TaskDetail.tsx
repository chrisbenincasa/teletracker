import React, { useEffect } from 'react';
import { DeepReadonly } from '../../types';
import { RouteComponentProps } from '@reach/router';
import { useDispatch, useSelector } from 'react-redux';
import { fetchTaskAsync } from './tasksSlice';
import { CircularProgress, Typography } from '@material-ui/core';

interface OwnProps {
  taskId?: string;
}

type Props = DeepReadonly<OwnProps & RouteComponentProps>;

export default function TaskDetail(props: Props) {
  const dispatch = useDispatch();
  const isLoading = useSelector((state) => state.tasks.isLoadingTaskDetail);
  const task = useSelector((state) => state.tasks.taskDetail);

  useEffect(() => {
    dispatch(fetchTaskAsync({ id: props.taskId! }));
  }, []);

  return (
    <>
      {isLoading && !task ? (
        <CircularProgress />
      ) : (
        <div>
          <Typography variant="h3">{task?.taskName}</Typography>
          <Typography variant="h4">{task?.id}</Typography>
          <pre>{JSON.stringify(task?.args, null, 4)}</pre>
        </div>
      )}
    </>
  );
}
