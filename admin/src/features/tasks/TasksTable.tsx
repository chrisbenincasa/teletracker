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
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
  Column,
  ColumnInstance,
  TableInstance,
  useFilters,
  useSortBy,
  useTable,
} from 'react-table';
import { useDebounce } from 'use-debounce/lib';
import { RootState } from '../../app/store';
import { DeepReadonly } from '../../types';
import { SearchTasksRequest, Task, TaskStatus } from '../../util/apiClient';
import TaskNameCell from './TaskNameCell';
import { fetchTasksAsync } from './tasksSlice';

export type Props = DeepReadonly<{
  showTaskFilter?: boolean;
  initialTaskName?: string;
  initialLimit?: number;
}>;

export default function TasksTable(props: Props) {
  const dispatch = useDispatch();
  const tasks: Task[] = useSelector((state: RootState) =>
    state.tasks.tasks.map((t) => ({ ...t })),
  );
  const [taskNameFilter, setTaskNameFilter] = useState(
    props.initialTaskName || '',
  );
  const [debouncedTaskNameFilter] = useDebounce(taskNameFilter, 250);
  const [statusSelectOpen, setStatusSelectOpen] = React.useState(false);
  const anchorRef = React.useRef<HTMLDivElement>(null);
  const [selectedStatus, setSelectedStatus] = React.useState<TaskStatus | null>(
    null,
  );
  const [copyBoxValue, setCopyBoxValue] = useState('');
  const idField = useRef<HTMLTextAreaElement>(null);

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
      {
        Header: 'Task Name',
        accessor: 'taskName',
        disableSortBy: true,
        Cell: (props) => <TaskNameCell {...props} />,
      },
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
      limit: props.initialLimit || 25,
      taskName:
        debouncedTaskNameFilter.length > 0
          ? debouncedTaskNameFilter
          : undefined,
      status: selectedStatus ? [selectedStatus] : undefined,
    };
  }, [props.initialLimit, sortBy, debouncedTaskNameFilter, selectedStatus]);

  useEffect(() => {
    console.log('querying....');
    startTaskFetch(requestParams);
  }, [startTaskFetch, requestParams]);

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
      <div>
        {props.showTaskFilter && (
          <TextField
            placeholder="Filter Task Name"
            id="standard-start-adornment"
            // className={clsx(classes.margin, classes.textField)}
            size="small"
            value={taskNameFilter}
            onChange={(ev) => setTaskNameFilter(ev.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <FilterList />
                </InputAdornment>
              ),
            }}
          />
        )}

        <ButtonGroup
          variant="contained"
          color="primary"
          ref={anchorRef}
          aria-label="split button"
        >
          <Button>
            {selectedStatus ? `Status: ${selectedStatus}` : 'Filter By Status'}
          </Button>
          <Button
            color="primary"
            size="small"
            aria-controls={statusSelectOpen ? 'split-button-menu' : undefined}
            aria-expanded={statusSelectOpen ? 'true' : undefined}
            aria-label="select merge strategy"
            aria-haspopup="menu"
            onClick={handleToggle}
          >
            <ArrowDropDown />
          </Button>
        </ButtonGroup>
        <Popper
          open={statusSelectOpen}
          anchorEl={anchorRef.current}
          role={undefined}
          transition
          disablePortal
          placement="bottom-end"
        >
          {({ TransitionProps, placement }) => (
            <Grow
              {...TransitionProps}
              style={{
                transformOrigin:
                  placement === 'bottom' ? 'center top' : 'center bottom',
              }}
            >
              <Paper>
                <ClickAwayListener onClickAway={handleClose}>
                  <MenuList id="split-button-menu">
                    <MenuItem
                      selected={selectedStatus === null}
                      onClick={(event) => handleMenuItemClick(event, null)}
                    >
                      None
                    </MenuItem>
                    {Object.entries(TaskStatus).map(([status, value]) => (
                      <MenuItem
                        key={status}
                        selected={status === selectedStatus}
                        onClick={(event) => handleMenuItemClick(event, value)}
                      >
                        {status}
                      </MenuItem>
                    ))}
                  </MenuList>
                </ClickAwayListener>
              </Paper>
            </Grow>
          )}
        </Popper>
      </div>
      <Paper>
        <TableContainer>
          <Table {...getTableProps()}>
            <TableHead>
              {headerGroups.map((group) => (
                <TableRow {...group.getHeaderGroupProps()}>
                  {group.headers.map(renderColumn)}
                </TableRow>
              ))}
            </TableHead>
            <TableBody {...getTableBodyProps()}>
              {rows.map((row) => {
                prepareRow(row);
                return (
                  <TableRow
                    {...row.getRowProps()}
                    hover
                    // onClick={() => handleRowClick(row)}
                  >
                    {row.cells.map((cell) => (
                      <TableCell {...cell.getCellProps()}>
                        {cell.render('Cell')}
                      </TableCell>
                    ))}
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
      <textarea
        ref={idField}
        readOnly
        style={{ top: 0, left: 0, position: 'fixed', display: 'none' }}
        value={copyBoxValue}
      ></textarea>
    </>
  );
}

TasksTable.defaultProps = {
  showTaskFilter: true,
};
