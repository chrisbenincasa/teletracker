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
import { FileCopy, Close, Storage } from '@material-ui/icons';
import CopyToClipboard from '../../components/CopyToClipboard';

interface OwnProps {
  taskId?: string;
}

type Props = DeepReadonly<OwnProps & RouteComponentProps>;

export default function TaskDetail(props: Props) {
  const dispatch = useDispatch();
  const isLoading = useSelector((state) => state.tasks.isLoadingTaskDetail);
  const task = useSelector((state) => state.tasks.taskDetail);
  const [snackbarOpen, setSnackbarOpen] = useState(false);

  useEffect(() => {
    dispatch(fetchTaskAsync({ id: props.taskId! }));
  }, [props.taskId]);

  return (
    <>
      {isLoading || !task ? (
        <CircularProgress />
      ) : (
        <main>
          <section>
            <div>
              <Typography variant="h3">{task?.taskName}</Typography>
              <div>
                <Typography variant="h4">
                  {task?.id}
                  <CopyToClipboard
                    value={task!.id}
                    onCopy={() => setSnackbarOpen(true)}
                  />
                  <Tooltip title="Raw Storage" placement="top">
                    <IconButton
                      component="a"
                      href={`https://search.internal.qa.teletracker.tv/tasks/_doc/${
                        task!.id
                      }`}
                      target="_blank"
                    >
                      <Storage />
                    </IconButton>
                  </Tooltip>
                </Typography>
              </div>
              <pre>{JSON.stringify(task?.args, null, 4)}</pre>
              <div>
                <Typography variant="h5">Logs location</Typography>
                <div style={{ display: 'flex' }}>
                  <pre>{task!.logUri}</pre>
                  <CopyToClipboard
                    value={task!.logUri ? task.logUri + '/' : ''}
                  />
                </div>
              </div>
            </div>
          </section>
          <section>
            <Typography variant="h4">Reschedule job</Typography>
            <textarea style={{ fontFamily: 'monospace' }}></textarea>
          </section>
          <section>
            <Typography variant="h4">
              Other instances of {task?.taskName}
            </Typography>
            <TasksTable
              showTaskFilter={false}
              initialTaskName={task.taskName}
              initialLimit={5}
            />
          </section>
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
        </main>
      )}
    </>
  );
}
