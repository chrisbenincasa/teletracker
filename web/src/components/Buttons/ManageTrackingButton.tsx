import React, { useEffect, useState } from 'react';
import { Button, makeStyles } from '@material-ui/core';
import { PlaylistAdd } from '@material-ui/icons';
import { Item, itemBelongsToLists } from '../../types/v2/Item';
import { useWidth } from '../../hooks/useWidth';

const useStyles = makeStyles(theme => ({
  buttonIcon: {
    [theme.breakpoints.down('sm')]: {
      fontSize: '1rem',
    },
    fontSIze: '2rem',
  },
  itemCTA: {
    whiteSpace: 'nowrap',
  },
}));

interface Props {
  itemDetail?: Item;
  onClick: () => void;
  style?: object;
  cta?: string;
  className?: string;
}

export default function ManageTrackingButton(props: Props) {
  const classes = useStyles();
  const width = useWidth();
  const isMobile = ['xs', 'sm'].includes(width);
  let [isTracked, setIsTracked] = useState(false);

  useEffect(() => {
    let belongsToLists: string[] =
      props && props.itemDetail ? itemBelongsToLists(props.itemDetail) : [];

    if (belongsToLists.length > 0) {
      setIsTracked(true);
    } else {
      setIsTracked(false);
    }
  });

  let trackingCTA = isTracked ? 'Manage Tracking' : 'Add to List';
  let trackingCTAMobile = isTracked ? 'Tracked' : 'Add to List';

  return (
    <div className={props.className} style={{ ...props.style }}>
      <Button
        size="small"
        variant="contained"
        fullWidth
        aria-label={props.cta || trackingCTA}
        onClick={props.onClick}
        color={isTracked ? 'primary' : undefined}
        startIcon={<PlaylistAdd className={classes.buttonIcon} />}
        className={classes.itemCTA}
      >
        {props.cta || isMobile ? trackingCTAMobile : trackingCTA}
      </Button>
    </div>
  );
}
