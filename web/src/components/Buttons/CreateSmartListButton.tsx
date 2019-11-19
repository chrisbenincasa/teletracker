import { AddCircle } from '@material-ui/icons';
import { IconButton, Typography } from '@material-ui/core';
import { default as React } from 'react';
import Tooltip from '@material-ui/core/Tooltip';

interface Props {
  onClick: () => void;
}

export default function CreateSmartListButton(props: Props) {
  return (
    <Tooltip title="Save as List" aria-label="save-as-list" placement="top">
      <IconButton onClick={props.onClick}>
        <AddCircle />
        <Typography variant="srOnly">Save as List</Typography>
      </IconButton>
    </Tooltip>
  );
}
