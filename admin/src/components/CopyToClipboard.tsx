import React, { useRef, useCallback } from 'react';
import { Tooltip, IconButton } from '@material-ui/core';
import { FileCopy } from '@material-ui/icons';
import { DeepReadonly } from '../types';

type Props = DeepReadonly<{
  value: string;
  onCopy?: () => void;
}>;

export default function CopyToClipboard(props: Props) {
  const idField = useRef<HTMLTextAreaElement>(null);

  const copyToClipboard = () => {
    idField.current?.select();
    document.execCommand('copy');
    // idField.current?.blur();
    if (props.onCopy) {
      props.onCopy();
    }
  };

  return (
    <span>
      <Tooltip title="Copy to Clipboard" placement="top">
        <IconButton onClick={copyToClipboard} size="small">
          <FileCopy />
        </IconButton>
      </Tooltip>
      <textarea
        ref={idField}
        readOnly
        style={{
          top: 0,
          left: 0,
          position: 'fixed',
          display: 'none',
        }}
        value={props.value}
      ></textarea>
    </span>
  );
}
