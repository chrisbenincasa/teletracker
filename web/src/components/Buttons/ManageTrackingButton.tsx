import React, { useEffect, useState } from 'react';
import { Button, makeStyles } from '@material-ui/core';
import { AddCircle, PlaylistAdd } from '@material-ui/icons';
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
}

export default function ManageTrackingButton(props: Props) {
  const classes = useStyles();
  const width = useWidth();
  const isMobile = ['xs', 'sm'].includes(width);
  let [isTracked, setIsTracked] = useState(false);

  const belongsToLists: string[] =
    props && props.itemDetail ? itemBelongsToLists(props.itemDetail) : [];

  console.log(props.itemDetail);

  useEffect(() => {
    let belongsToLists: string[] =
      props && props.itemDetail ? itemBelongsToLists(props.itemDetail) : [];
    // console.log(belongsToLists);
    if (belongsToLists.length > 0) {
      setIsTracked(true);
    } else {
      setIsTracked(false);
    }
    console.log(belongsToLists);
  });

  let trackingCTA = isTracked ? 'Manage Tracking' : 'Add to List';
  let trackingCTAMobile = isTracked ? 'Tracked' : 'Add to List';

  return (
    <div className={classes.itemCTA} style={{ ...props.style }}>
      <Button
        size="small"
        variant="contained"
        fullWidth
        aria-label={props.cta || trackingCTA}
        onClick={props.onClick}
        color={belongsToLists.length > 0 ? 'primary' : undefined}
        startIcon={<PlaylistAdd className={classes.buttonIcon} />}
      >
        {props.cta || isMobile ? trackingCTAMobile : trackingCTA}
      </Button>
    </div>
  );
}
