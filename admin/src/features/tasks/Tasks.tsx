import React, { useEffect } from 'react';
import { DeepReadonly } from '../../types';
import { RouteComponentProps } from '@reach/router';
import MaterialTable from 'material-table';
import { useDispatch, useSelector } from 'react-redux';
import { fetchTasksAsync } from './tasksSlice';
import { RootState } from '../../app/store';
import { SearchTasksRequest, Task } from '../../util/apiClient';

type Props = DeepReadonly<{} & RouteComponentProps>;

export default function Tasks(props: Props) {
  // const classes = useStyles();
  const dispatch = useDispatch();

  const isLoading = useSelector((state: RootState) => state.tasks.isLoading);
  const tasks = useSelector((state: RootState) =>
    state.tasks.tasks.map((t) => ({ ...t })),
  );

  const startTaskFetch = (req: SearchTasksRequest) => {
    return dispatch(fetchTasksAsync(req));
  };

  useEffect(() => {
    startTaskFetch({ limit: 25 });
  }, []);

  const handleOrderChange = (columnId: number, columnDir: 'asc' | 'desc') => {
    startTaskFetch({ limit: 25 });
  };

  return (
    <div>
      <div style={{ maxWidth: '100%' }}>
        <MaterialTable
          columns={[
            { title: 'Name', field: 'taskName' },
            { title: 'Status', field: 'status' },
            { title: 'Started At', field: 'startedAt' },
            { title: 'Finished At', field: 'finishedAt' },
            { title: 'Host', field: 'hostname' },
          ]}
          data={[...tasks]}
          title="Tasks"
          options={{
            pageSize: 25,
            pageSizeOptions: [25, 50, 100],
          }}
          isLoading={isLoading}
          onOrderChange={handleOrderChange}
        />
      </div>
    </div>
  );
}
