import { IconButton, Snackbar } from '@material-ui/core';
import { Close, Search } from '@material-ui/icons';
import { navigate } from '@reach/router';
import React, { useCallback, useRef, useState } from 'react';
import { CellProps } from 'react-table';
import CopyToClipboard from '../../components/CopyToClipboard';
import { Task } from '../../util/apiClient';

export default function TaskNameCell(props: CellProps<Task, string>) {
  const [snackbarOpen, setSnackbarOpen] = useState(false);

  const handleRowClick = useCallback(() => {
    navigate(`/tasks/${props.row.original.id}`).finally(() => {
      console.log('what happened');
    });
  }, [props.row.original.id]);

  return (
    <div>
      {props.cell.value}
      <>
        <IconButton size="small" onClick={handleRowClick}>
          <Search />
        </IconButton>
        <CopyToClipboard
          value={props.row.original.taskName}
          onCopy={() => setSnackbarOpen(true)}
        />
      </>
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
        message={`Copied "${props.row.original.taskName}" to clipboard`}
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
    </div>
  );
}
