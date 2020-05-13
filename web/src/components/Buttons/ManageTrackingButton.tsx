import React, { useEffect, useState } from 'react';
import { Button, makeStyles } from '@material-ui/core';
import { PlaylistAdd } from '@material-ui/icons';
import { Item, itemBelongsToLists } from '../../types/v2/Item';
import { useWidth } from '../../hooks/useWidth';
import { Id } from '../../types/v2';
import useStateSelector from '../../hooks/useStateSelector';
import selectItem from '../../selectors/selectItem';

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
  readonly itemId?: Id;
  readonly onClick: () => void;
  readonly style?: object;
  readonly cta?: string;
  readonly className?: string;
}

export default function ManageTrackingButton(props: Props) {
  const classes = useStyles();
  const width = useWidth();
  const isMobile = ['xs', 'sm'].includes(width);
  const itemDetail = props.itemId
    ? useStateSelector(state => selectItem(state, props.itemId!))
    : undefined;
  let [isTracked, setIsTracked] = useState(false);

  useEffect(() => {
    let belongsToLists: string[] = itemDetail
      ? itemBelongsToLists(itemDetail)
      : [];

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
