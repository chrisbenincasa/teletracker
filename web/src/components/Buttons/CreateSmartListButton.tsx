import React from 'react';
import { Button, Tooltip } from '@material-ui/core';
import { AddCircle } from '@material-ui/icons';
import { useWidth } from '../../hooks/useWidth';

interface Props {
  onClick: () => void;
}

export default function CreateSmartListButton(props: Props) {
  const width = useWidth();
  const isMobile = ['xs', 'sm'].includes(width);

  return (
    <Tooltip
      title="Save this search as a dynamic list"
      aria-label="save-as-list"
      placement="top"
    >
      <Button
        size="small"
        onClick={props.onClick}
        variant="contained"
        color="primary"
        aria-label="Create Smart List"
        startIcon={<AddCircle />}
        fullWidth={isMobile}
      >
        Create Smart List
      </Button>
    </Tooltip>
  );
}
