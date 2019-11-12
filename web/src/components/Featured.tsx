import {
  CardMedia,
  Fade,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import React, { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import ManageTracking from '../components/ManageTracking';
import { ResponsiveImage } from '../components/ResponsiveImage';
import { ApiItem } from '../types/v2';
import { Item } from '../types/v2/Item';
import AddToListDialog from './AddToListDialog';
import withUser, { WithUserProps } from './withUser';

const useStyles = makeStyles((theme: Theme) => ({
  backdropContainer: {
    height: 'auto',
    overflow: 'hidden',
    top: 0,
    width: '100%',
  },
  backdropGradient: {
    position: 'absolute',
    top: 0,
    width: '100%',
    height: '100%',
    backgroundImage:
      'linear-gradient(to bottom, rgba(255, 255, 255,0) 0%,rgba(48, 48, 48,1) 100%)',
  },
  posterContainer: {
    display: 'flex',
    flex: '0 1 auto',
    flexDirection: 'column',
    position: 'absolute',
    height: 'auto',
    top: theme.spacing(3),
    left: theme.spacing(3),
    width: '25%',
    [theme.breakpoints.up('md')]: {
      width: 225,
    },
  },
  title: {
    textAlign: 'right',
    width: '70%',
    alignSelf: 'flex-end',
    fontSize: theme.typography.h5.fontSize,
    fontWeight: 700,
  },
  titleContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-start',
    position: 'absolute',
    bottom: 0,
    right: theme.spacing(3),
    marginBottom: theme.spacing(3),
    width: '65%',
  },
}));

interface OwnProps {
  featuredItem: Item;
}

type Props = OwnProps & WithUserProps;

function Featured(props: Props) {
  const [manageTrackingModalOpen, setManageTrackingModalOpen] = useState<
    boolean
  >(false);
  const [imageLoading, setImageLoading] = useState<boolean>(true);
  const classes = useStyles();
  const { featuredItem, userSelf } = props;

  useEffect(() => {
    setImageLoading(true);
    return setImageLoading(false);
  }, [featuredItem]);

  const renderTitle = (item: ApiItem) => {
    const title = item.original_title || '';

    return (
      <div className={classes.titleContainer}>
        <Typography color="inherit" variant="h3" className={classes.title}>
          {`${title}`}
        </Typography>
      </div>
    );
  };

  const imageLoaded = () => {
    setImageLoading(false);
  };

  return featuredItem ? (
    <Fade in={!imageLoading}>
      <div style={{ position: 'relative' }}>
        <div className={classes.backdropContainer}>
          <ResponsiveImage
            item={featuredItem}
            imageType="backdrop"
            imageStyle={{
              objectFit: 'cover',
              width: '100%',
              height: '100%',
              maxHeight: 424,
            }}
            pictureStyle={{
              display: 'block',
            }}
            loadCallback={imageLoaded}
          />
          <div className={classes.backdropGradient} />
        </div>

        <div className={classes.posterContainer}>
          <RouterLink
            to={featuredItem.relativeUrl}
            style={{
              display: 'block',
              height: '100%',
              textDecoration: 'none',
            }}
          >
            <CardMedia
              src={imagePlaceholder}
              item={featuredItem}
              component={ResponsiveImage}
              imageType="poster"
              imageStyle={{
                boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
                maxWidth: '100%',
                maxHeight: '100%',
              }}
              pictureStyle={{
                height: '100%',
                width: '100%',
                display: 'block',
              }}
            />
          </RouterLink>

          <ManageTracking itemDetail={featuredItem} style={{ maxWidth: 225 }} />
          <AddToListDialog
            open={manageTrackingModalOpen}
            onClose={() => setManageTrackingModalOpen(false)}
            userSelf={userSelf!}
            item={featuredItem}
          />
        </div>
        {renderTitle(featuredItem)}
      </div>
    </Fade>
  ) : null;
}

export default withUser(Featured);
