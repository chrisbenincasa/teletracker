import { RouteComponentProps } from '@reach/router';
import React from 'react';
import { DeepReadonly } from '../../types';
import TasksTable from './TasksTable';

type Props = DeepReadonly<{} & RouteComponentProps>;

export default function Tasks(props: Props) {
  return (
    <>
      <TasksTable />
    </>
  );
}
