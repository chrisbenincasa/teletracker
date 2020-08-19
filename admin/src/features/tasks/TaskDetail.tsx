import React, { useEffect, useCallback, useState, useRef } from 'react';
import { DeepReadonly } from '../../types';
import { RouteComponentProps } from '@reach/router';
import { useDispatch, useSelector } from 'react-redux';
import { fetchTaskAsync } from './tasksSlice';
import {
  CircularProgress,
  Typography,
  IconButton,
  Tooltip,
  Snackbar,
} from '@material-ui/core';
import TasksTable from './TasksTable';
import { FileCopy, Close } from '@material-ui/icons';

interface OwnProps {
  taskId?: string;
}

type Props = DeepReadonly<OwnProps & RouteComponentProps>;

export default function TaskDetail(props: Props) {
  const dispatch = useDispatch();
  const isLoading = useSelector((state) => state.tasks.isLoadingTaskDetail);
  const task = useSelector((state) => state.tasks.taskDetail);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const idField = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    dispatch(fetchTaskAsync({ id: props.taskId! }));
  }, [props.taskId]);

  const copyToClipboard = useCallback(() => {
    idField.current?.focus();
    idField.current?.select();
    document.execCommand('copy');
    idField.current?.blur();
    setSnackbarOpen(true);
  }, []);

  return (
    <>
      {isLoading || !task ? (
        <CircularProgress />
      ) : (
        <>
          <div>
            <Typography variant="h3">{task?.taskName}</Typography>
            <div>
              <Typography variant="h4">
                {task?.id}
                <Tooltip title="Copy to Clipboard" placement="top">
                  <IconButton onClick={copyToClipboard}>
                    <FileCopy />
                  </IconButton>
                </Tooltip>
              </Typography>
              <textarea
                ref={idField}
                readOnly
                style={{ top: 0, left: 0, position: 'fixed', display: 'none' }}
                value={task?.id}
              ></textarea>
            </div>
            <pre>{JSON.stringify(task?.args, null, 4)}</pre>
          </div>
          <Typography variant="h4">
            Other instances of {task?.taskName}
          </Typography>
          <TasksTable
            showTaskFilter={false}
            initialTaskName={task.taskName}
            initialLimit={5}
          />
          <Snackbar
            anchorOrigin={{
              vertical: 'bottom',
              horizontal: 'center',
            }}
            open={snackbarOpen}
            autoHideDuration={5000}
            onClose={() => setSnackbarOpen(false)}
            onExited={() => setSnackbarOpen(false)}
            // className={classes.snackbarContainer}
            message={'ID copied to clipboard'}
            action={
              <React.Fragment>
                <IconButton
                  aria-label="close"
                  color="inherit"
                  // className={classes.close}
                  onClick={() => setSnackbarOpen(false)}
                >
                  <Close />
                </IconButton>
              </React.Fragment>
            }
          />
        </>
      )}
    </>
  );
}
