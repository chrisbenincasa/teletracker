import React, { useCallback, useState, useRef } from 'react';
import { Task } from '../../util/apiClient';
import { CellProps } from 'react-table';
import { navigate } from '@reach/router';
import { IconButton, Snackbar } from '@material-ui/core';
import { Search, FileCopy, Close } from '@material-ui/icons';

export default function TaskNameCell(props: CellProps<Task, string>) {
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const idField = useRef<HTMLTextAreaElement>(null);

  const handleRowClick = useCallback(() => {
    navigate(`/tasks/${props.row.original.id}`).finally(() => {
      console.log('what happened');
    });
  }, [props.row.original.id]);

  const copyToClipboard = useCallback(() => {
    idField.current?.focus();
    idField.current?.select();
    document.execCommand('copy');
    idField.current?.blur();
    setSnackbarOpen(true);
  }, [props.row.original.taskName]);

  return (
    <div>
      {props.cell.value}
      <>
        <IconButton size="small" onClick={handleRowClick}>
          <Search />
        </IconButton>
        <IconButton size="small" onClick={copyToClipboard}>
          <FileCopy />
        </IconButton>
        <textarea
          ref={idField}
          readOnly
          style={{ top: 0, left: 0, position: 'fixed', display: 'none' }}
          value={props.row.original.taskName}
        ></textarea>
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
