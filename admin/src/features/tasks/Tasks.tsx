import {
  Button,
  ButtonGroup,
  ClickAwayListener,
  Grow,
  InputAdornment,
  MenuItem,
  MenuList,
  Paper,
  Popper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  TextField,
} from '@material-ui/core';
import { ArrowDropDown, FilterList } from '@material-ui/icons';
import { navigate, RouteComponentProps } from '@reach/router';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
// import MaterialTable, { Column } from 'material-table';
import { useDispatch, useSelector } from 'react-redux';
import {
  Column,
  ColumnInstance,
  Row,
  // Column,
  TableInstance,
  useFilters,
  useSortBy,
  useTable,
} from 'react-table';
import { useDebounce } from 'use-debounce/lib';
import { RootState } from '../../app/store';
import { DeepReadonly } from '../../types';
import { SearchTasksRequest, Task, TaskStatus } from '../../util/apiClient';
import { fetchTasksAsync } from './tasksSlice';
import TasksTable from './TasksTable';

type Props = DeepReadonly<{} & RouteComponentProps>;

export default function Tasks(props: Props) {
  const dispatch = useDispatch();
  const tasks: Task[] = useSelector((state: RootState) =>
    state.tasks.tasks.map((t) => ({ ...t })),
  );
  const [taskNameFilter, setTaskNameFilter] = useState('');
  const [debouncedTaskNameFilter] = useDebounce(taskNameFilter, 250);
  const [statusSelectOpen, setStatusSelectOpen] = React.useState(false);
  const anchorRef = React.useRef<HTMLDivElement>(null);
  const [selectedStatus, setSelectedStatus] = React.useState<TaskStatus | null>(
    null,
  );

  const handleToggle = () => {
    setStatusSelectOpen((prevOpen) => !prevOpen);
  };

  const handleClose = () => setStatusSelectOpen(false);

  const handleMenuItemClick = (
    event: React.MouseEvent<HTMLLIElement, MouseEvent>,
    status: TaskStatus | null,
  ) => {
    setSelectedStatus(status);
    setStatusSelectOpen(false);
    console.log('menu');
  };

  const startTaskFetch = useCallback(
    (req: SearchTasksRequest) => {
      return dispatch(fetchTasksAsync(req));
    },
    [dispatch],
  );

  const columns: Column<Task>[] = useMemo(
    () => [
      { Header: 'Task Name', accessor: 'taskName', disableSortBy: true },
      { Header: 'Status', accessor: 'status', disableSortBy: true },
      { Header: 'Created At', accessor: 'createdAt' },
      { Header: 'Started At', accessor: 'startedAt' },
      { Header: 'Finished At', accessor: 'finishedAt' },
      { Header: 'Hostname', accessor: 'hostname', disableSortBy: true },
    ],
    [],
  );

  const {
    headerGroups,
    getTableProps,
    getTableBodyProps,
    prepareRow,
    rows,
    state: { sortBy, filters, globalFilter },
  }: TableInstance<Task> = useTable(
    {
      columns,
      data: [...tasks],
      // initialState: { globalFilter: currentTaskNameFilter ? [{}] },
      manualSortBy: true,
      manualFilters: true,
      manualGlobalFilter: true,
    },
    useFilters,
    useSortBy,
  );

  const requestParams: SearchTasksRequest = useMemo(() => {
    return {
      sort: sortBy.length > 0 ? sortBy[0].id : undefined,
      desc: sortBy.length > 0 ? sortBy[0].desc : undefined,
      limit: 25,
      taskName:
        debouncedTaskNameFilter.length > 0
          ? debouncedTaskNameFilter
          : undefined,
      status: selectedStatus ? [selectedStatus] : undefined,
    };
  }, [sortBy, debouncedTaskNameFilter, selectedStatus]);

  useEffect(() => {
    console.log('querying....');
    startTaskFetch(requestParams);
  }, [startTaskFetch, requestParams]);

  const handleRowClick = (row: Row<Task>) => {
    navigate(`/tasks/${row.original.id}`);
  };

  const renderColumn = (column: ColumnInstance<Task>) => {
    let isCurrentlySorted = sortBy.length > 0 && sortBy[0].id === column.id;
    let label: React.ReactNode;
    if (column.canSort) {
      label = (
        <TableSortLabel
          {...column.getHeaderProps(column.getSortByToggleProps())}
          active={isCurrentlySorted}
          direction={isCurrentlySorted && sortBy[0].desc ? 'desc' : 'asc'}
          style={{ width: '100%' }}
        >
          {column.render('Header')}
        </TableSortLabel>
      );
    } else {
      label = (
        <span style={{ display: 'inline-flex', width: '100%' }}>
          {column.render('Header')}
        </span>
      );
    }

    return (
      <TableCell
        {...column.getHeaderProps()}
        // align={headCell.numeric ? 'right' : 'left'}
        // padding={headCell.disablePadding ? 'none' : 'default'}
        sortDirection={isCurrentlySorted && sortBy[0].desc ? 'desc' : 'asc'}
      >
        <div style={{ display: 'flex', flexWrap: 'wrap' }}>{label}</div>
      </TableCell>
    );
  };

  return (
    <>
      <TasksTable />
    </>
  );
}
