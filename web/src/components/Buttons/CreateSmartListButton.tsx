import React from 'react';
import { IconButton, Typography, Tooltip } from '@material-ui/core';
import { AddCircle } from '@material-ui/icons';

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
